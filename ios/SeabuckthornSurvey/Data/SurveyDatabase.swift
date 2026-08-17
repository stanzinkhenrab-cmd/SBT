import Foundation
import SQLite3

/// Sentinel telling SQLite to copy bound text rather than borrow it.
private let SQLITE_TRANSIENT = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

enum DatabaseError: Error, LocalizedError {
    case open(String)
    case statement(String)

    var errorDescription: String? {
        switch self {
        case .open(let message): return "The survey database could not be opened: \(message)"
        case .statement(let message): return "The survey database rejected an operation: \(message)"
        }
    }
}

/// Storage for survey records.
///
/// SQLite is used directly rather than through Core Data: the schema is two small
/// tables shared with the Android app, and writing the SQL keeps both platforms
/// byte-for-byte compatible - including the transaction that hands out Survey IDs,
/// which is the one operation that must never go wrong.
final class SurveyDatabase {

    private var handle: OpaquePointer?
    private let queue = DispatchQueue(label: "com.kvkleh.sbtsurvey.database")

    /// Opens the on-disk database, creating it on first launch.
    convenience init(url: URL) throws {
        try self.init(path: url.path)
    }

    /// Opens a database at a raw SQLite path. `:memory:` is honoured as such.
    init(path: String) throws {
        var db: OpaquePointer?
        let flags = SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX
        guard sqlite3_open_v2(path, &db, flags, nil) == SQLITE_OK, let opened = db else {
            let message = db.map { String(cString: sqlite3_errmsg($0)) } ?? "unknown error"
            sqlite3_close(db)
            throw DatabaseError.open(message)
        }
        handle = opened
        try execute("PRAGMA journal_mode = WAL;")
        try execute("PRAGMA foreign_keys = ON;")
        try execute("PRAGMA busy_timeout = 5000;")
        try createSchema()
    }

    /// Opens a database that lives only for the lifetime of this object. Used by tests.
    static func inMemory() throws -> SurveyDatabase {
        try SurveyDatabase(path: ":memory:")
    }

    deinit {
        if let handle { sqlite3_close(handle) }
    }

    // MARK: - Schema

    private func createSchema() throws {
        try execute("""
            CREATE TABLE IF NOT EXISTS surveys (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                surveyId TEXT NOT NULL,
                status TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                savedAt INTEGER,
                surveyorName TEXT NOT NULL DEFAULT '',
                designation TEXT NOT NULL DEFAULT '',
                organization TEXT NOT NULL DEFAULT '',
                district TEXT NOT NULL DEFAULT '',
                block TEXT NOT NULL DEFAULT '',
                village TEXT NOT NULL DEFAULT '',
                site TEXT NOT NULL DEFAULT '',
                photoFileName TEXT,
                latitude REAL,
                longitude REAL,
                altitude REAL,
                accuracyM REAL,
                locationCapturedAt INTEGER,
                shrubType TEXT,
                plantHeight REAL,
                plantHeightUnit TEXT NOT NULL DEFAULT 'm',
                maturityStage TEXT,
                harvestDate INTEGER,
                berryDiameterMm REAL,
                tssBrix REAL,
                easeOfHarvest TEXT
            );
            """)
        try execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_surveys_surveyId ON surveys(surveyId);")
        try execute("CREATE INDEX IF NOT EXISTS idx_surveys_status ON surveys(status);")
        try execute("""
            CREATE TABLE IF NOT EXISTS id_counter (
                year INTEGER PRIMARY KEY,
                lastSequence INTEGER NOT NULL
            );
            """)
    }

    // MARK: - Reads

    func survey(id: Int64) throws -> Survey? {
        try queue.sync {
            try readOne("SELECT * FROM surveys WHERE id = ?;") { statement in
                sqlite3_bind_int64(statement, 1, id)
            }
        }
    }

    func survey(surveyId: String) throws -> Survey? {
        try queue.sync {
            try readOne("SELECT * FROM surveys WHERE surveyId = ? LIMIT 1;") { statement in
                sqlite3_bind_text(statement, 1, surveyId, -1, SQLITE_TRANSIENT)
            }
        }
    }

    /// Saved records only, newest first.
    func savedSurveys() throws -> [Survey] {
        try queue.sync {
            try readAll(
                "SELECT * FROM surveys WHERE status = ? ORDER BY createdAt DESC, id DESC;"
            ) { statement in
                sqlite3_bind_text(statement, 1, Survey.statusSaved, -1, SQLITE_TRANSIENT)
            }
        }
    }

    /// Every record including the unfinished draft, newest first. Used by export.
    func allSurveys() throws -> [Survey] {
        try queue.sync {
            try readAll("SELECT * FROM surveys ORDER BY createdAt DESC, id DESC;") { _ in }
        }
    }

    func latestDraft() throws -> Survey? {
        try queue.sync {
            try readOne(
                "SELECT * FROM surveys WHERE status = ? ORDER BY updatedAt DESC LIMIT 1;"
            ) { statement in
                sqlite3_bind_text(statement, 1, Survey.statusDraft, -1, SQLITE_TRANSIENT)
            }
        }
    }

    func knownVillages() throws -> [String] {
        try queue.sync {
            var villages: [String] = []
            try withStatement(
                "SELECT DISTINCT village FROM surveys WHERE village <> '' ORDER BY village COLLATE NOCASE;"
            ) { statement in
                while sqlite3_step(statement) == SQLITE_ROW {
                    if let text = sqlite3_column_text(statement, 0) {
                        villages.append(String(cString: text))
                    }
                }
            }
            return villages
        }
    }

    // MARK: - Writes

    /// Allocates the next free identifier for the year and inserts the record with
    /// it, in one transaction, so two rapid taps on "New Survey" cannot collide.
    @discardableResult
    func insertWithNewSurveyId(_ template: Survey, now: Date = Date()) throws -> Survey {
        try queue.sync {
            try execute("BEGIN IMMEDIATE;")
            do {
                let year = Calendar.current.component(.year, from: now)
                var sequence = (try currentSequence(year: year) ?? 0) + 1
                var candidate = Survey.formatSurveyId(year: year, sequence: sequence)
                // Defensive: a restored database may hold identifiers beyond the
                // counter. Step over anything already taken rather than failing.
                while try existsUnlocked(surveyId: candidate) {
                    sequence += 1
                    candidate = Survey.formatSurveyId(year: year, sequence: sequence)
                }
                try upsertCounter(year: year, sequence: sequence)

                var record = template
                record.surveyId = candidate
                record.status = Survey.statusDraft
                record.createdAt = now
                record.updatedAt = now
                record.savedAt = nil
                let rowId = try insertUnlocked(record)
                record.id = rowId
                try execute("COMMIT;")
                return record
            } catch {
                try? execute("ROLLBACK;")
                throw error
            }
        }
    }

    func update(_ survey: Survey) throws {
        try queue.sync {
            try withStatement("""
                UPDATE surveys SET
                    surveyId = ?, status = ?, createdAt = ?, updatedAt = ?, savedAt = ?,
                    surveyorName = ?, designation = ?, organization = ?,
                    district = ?, block = ?, village = ?, site = ?,
                    photoFileName = ?,
                    latitude = ?, longitude = ?, altitude = ?, accuracyM = ?, locationCapturedAt = ?,
                    shrubType = ?, plantHeight = ?, plantHeightUnit = ?,
                    maturityStage = ?, harvestDate = ?,
                    berryDiameterMm = ?, tssBrix = ?, easeOfHarvest = ?
                WHERE id = ?;
                """) { statement in
                let last = bindFields(survey, to: statement, startingAt: 1)
                sqlite3_bind_int64(statement, last, survey.id)
                guard sqlite3_step(statement) == SQLITE_DONE else {
                    throw DatabaseError.statement(self.lastErrorMessage())
                }
            }
        }
    }

    func delete(id: Int64) throws {
        try queue.sync {
            try withStatement("DELETE FROM surveys WHERE id = ?;") { statement in
                sqlite3_bind_int64(statement, 1, id)
                guard sqlite3_step(statement) == SQLITE_DONE else {
                    throw DatabaseError.statement(self.lastErrorMessage())
                }
            }
        }
    }

    /// Rewinds the per-year counter. Exists so tests can simulate a restored
    /// database whose counter has fallen behind the records already in the table.
    func resetCounterForTesting(year: Int, sequence: Int) throws {
        try queue.sync { try upsertCounter(year: year, sequence: sequence) }
    }

    func savedCount() throws -> Int {
        try queue.sync {
            var count = 0
            try withStatement("SELECT COUNT(*) FROM surveys WHERE status = ?;") { statement in
                sqlite3_bind_text(statement, 1, Survey.statusSaved, -1, SQLITE_TRANSIENT)
                if sqlite3_step(statement) == SQLITE_ROW {
                    count = Int(sqlite3_column_int64(statement, 0))
                }
            }
            return count
        }
    }

    // MARK: - Unlocked helpers (callers already hold the queue)

    private func currentSequence(year: Int) throws -> Int? {
        var sequence: Int?
        try withStatement("SELECT lastSequence FROM id_counter WHERE year = ?;") { statement in
            sqlite3_bind_int64(statement, 1, Int64(year))
            if sqlite3_step(statement) == SQLITE_ROW {
                sequence = Int(sqlite3_column_int64(statement, 0))
            }
        }
        return sequence
    }

    private func upsertCounter(year: Int, sequence: Int) throws {
        try withStatement(
            "INSERT OR REPLACE INTO id_counter (year, lastSequence) VALUES (?, ?);"
        ) { statement in
            sqlite3_bind_int64(statement, 1, Int64(year))
            sqlite3_bind_int64(statement, 2, Int64(sequence))
            guard sqlite3_step(statement) == SQLITE_DONE else {
                throw DatabaseError.statement(self.lastErrorMessage())
            }
        }
    }

    private func existsUnlocked(surveyId: String) throws -> Bool {
        var found = false
        try withStatement("SELECT 1 FROM surveys WHERE surveyId = ? LIMIT 1;") { statement in
            sqlite3_bind_text(statement, 1, surveyId, -1, SQLITE_TRANSIENT)
            found = sqlite3_step(statement) == SQLITE_ROW
        }
        return found
    }

    private func insertUnlocked(_ survey: Survey) throws -> Int64 {
        var rowId: Int64 = 0
        try withStatement("""
            INSERT INTO surveys (
                surveyId, status, createdAt, updatedAt, savedAt,
                surveyorName, designation, organization,
                district, block, village, site,
                photoFileName,
                latitude, longitude, altitude, accuracyM, locationCapturedAt,
                shrubType, plantHeight, plantHeightUnit,
                maturityStage, harvestDate,
                berryDiameterMm, tssBrix, easeOfHarvest
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
            """) { statement in
            _ = bindFields(survey, to: statement, startingAt: 1)
            guard sqlite3_step(statement) == SQLITE_DONE else {
                throw DatabaseError.statement(self.lastErrorMessage())
            }
            rowId = sqlite3_last_insert_rowid(self.handle)
        }
        return rowId
    }

    /// Binds the 26 record columns in schema order and returns the next free index.
    private func bindFields(_ survey: Survey, to statement: OpaquePointer?, startingAt start: Int32) -> Int32 {
        var index = start

        func bindText(_ value: String) {
            sqlite3_bind_text(statement, index, value, -1, SQLITE_TRANSIENT)
            index += 1
        }
        func bindOptionalText(_ value: String?) {
            if let value {
                sqlite3_bind_text(statement, index, value, -1, SQLITE_TRANSIENT)
            } else {
                sqlite3_bind_null(statement, index)
            }
            index += 1
        }
        func bindDate(_ value: Date) {
            sqlite3_bind_int64(statement, index, value.millisecondsSince1970)
            index += 1
        }
        func bindOptionalDate(_ value: Date?) {
            if let value {
                sqlite3_bind_int64(statement, index, value.millisecondsSince1970)
            } else {
                sqlite3_bind_null(statement, index)
            }
            index += 1
        }
        func bindOptionalDouble(_ value: Double?) {
            if let value {
                sqlite3_bind_double(statement, index, value)
            } else {
                sqlite3_bind_null(statement, index)
            }
            index += 1
        }

        bindText(survey.surveyId)
        bindText(survey.status)
        bindDate(survey.createdAt)
        bindDate(survey.updatedAt)
        bindOptionalDate(survey.savedAt)
        bindText(survey.surveyorName)
        bindText(survey.designation)
        bindText(survey.organization)
        bindText(survey.district)
        bindText(survey.block)
        bindText(survey.village)
        bindText(survey.site)
        bindOptionalText(survey.photoFileName)
        bindOptionalDouble(survey.latitude)
        bindOptionalDouble(survey.longitude)
        bindOptionalDouble(survey.altitude)
        bindOptionalDouble(survey.accuracyM)
        bindOptionalDate(survey.locationCapturedAt)
        bindOptionalText(survey.shrubType)
        bindOptionalDouble(survey.plantHeight)
        bindText(survey.plantHeightUnit)
        bindOptionalText(survey.maturityStage)
        bindOptionalDate(survey.harvestDate)
        bindOptionalDouble(survey.berryDiameterMm)
        bindOptionalDouble(survey.tssBrix)
        bindOptionalText(survey.easeOfHarvest)

        return index
    }

    // MARK: - Statement plumbing

    private func execute(_ sql: String) throws {
        guard sqlite3_exec(handle, sql, nil, nil, nil) == SQLITE_OK else {
            throw DatabaseError.statement(lastErrorMessage())
        }
    }

    private func withStatement(_ sql: String, body: (OpaquePointer?) throws -> Void) throws {
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(handle, sql, -1, &statement, nil) == SQLITE_OK else {
            throw DatabaseError.statement(lastErrorMessage())
        }
        defer { sqlite3_finalize(statement) }
        try body(statement)
    }

    private func readOne(_ sql: String, bind: (OpaquePointer?) -> Void) throws -> Survey? {
        var result: Survey?
        try withStatement(sql) { statement in
            bind(statement)
            if sqlite3_step(statement) == SQLITE_ROW {
                result = SurveyDatabase.read(from: statement)
            }
        }
        return result
    }

    private func readAll(_ sql: String, bind: (OpaquePointer?) -> Void) throws -> [Survey] {
        var results: [Survey] = []
        try withStatement(sql) { statement in
            bind(statement)
            while sqlite3_step(statement) == SQLITE_ROW {
                results.append(SurveyDatabase.read(from: statement))
            }
        }
        return results
    }

    private func lastErrorMessage() -> String {
        guard let handle else { return "no database handle" }
        return String(cString: sqlite3_errmsg(handle))
    }

    /// Reads a row selected with `SELECT *`, so the column order is the schema order.
    private static func read(from statement: OpaquePointer?) -> Survey {
        func text(_ index: Int32) -> String {
            guard let value = sqlite3_column_text(statement, index) else { return "" }
            return String(cString: value)
        }
        func optionalText(_ index: Int32) -> String? {
            guard sqlite3_column_type(statement, index) != SQLITE_NULL,
                  let value = sqlite3_column_text(statement, index) else { return nil }
            return String(cString: value)
        }
        func optionalDouble(_ index: Int32) -> Double? {
            guard sqlite3_column_type(statement, index) != SQLITE_NULL else { return nil }
            return sqlite3_column_double(statement, index)
        }
        func date(_ index: Int32) -> Date {
            Date(millisecondsSince1970: sqlite3_column_int64(statement, index))
        }
        func optionalDate(_ index: Int32) -> Date? {
            guard sqlite3_column_type(statement, index) != SQLITE_NULL else { return nil }
            return Date(millisecondsSince1970: sqlite3_column_int64(statement, index))
        }

        var survey = Survey()
        survey.id = sqlite3_column_int64(statement, 0)
        survey.surveyId = text(1)
        survey.status = text(2)
        survey.createdAt = date(3)
        survey.updatedAt = date(4)
        survey.savedAt = optionalDate(5)
        survey.surveyorName = text(6)
        survey.designation = text(7)
        survey.organization = text(8)
        survey.district = text(9)
        survey.block = text(10)
        survey.village = text(11)
        survey.site = text(12)
        survey.photoFileName = optionalText(13)
        survey.latitude = optionalDouble(14)
        survey.longitude = optionalDouble(15)
        survey.altitude = optionalDouble(16)
        survey.accuracyM = optionalDouble(17)
        survey.locationCapturedAt = optionalDate(18)
        survey.shrubType = optionalText(19)
        survey.plantHeight = optionalDouble(20)
        survey.plantHeightUnit = text(21)
        survey.maturityStage = optionalText(22)
        survey.harvestDate = optionalDate(23)
        survey.berryDiameterMm = optionalDouble(24)
        survey.tssBrix = optionalDouble(25)
        survey.easeOfHarvest = optionalText(26)
        return survey
    }
}

extension Date {
    /// Epoch milliseconds, matching how the Android build stores instants.
    var millisecondsSince1970: Int64 {
        Int64((timeIntervalSince1970 * 1000).rounded())
    }

    init(millisecondsSince1970: Int64) {
        self.init(timeIntervalSince1970: Double(millisecondsSince1970) / 1000)
    }
}
