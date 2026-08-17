#!/usr/bin/env python3
"""Generate SeabuckthornSurvey.xcodeproj from the source tree.

The project file is generated rather than hand-maintained so that adding a Swift
file is just adding a file: re-run this script and every reference, build-file
entry and group is rebuilt consistently. Identifiers are derived from a hash of
each path, so regenerating produces byte-identical output and diffs stay readable.

    python3 ios/tools/generate_xcodeproj.py
"""
from __future__ import annotations

import hashlib
import os
import shutil

IOS_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROJECT_NAME = "SeabuckthornSurvey"
APP_DIR = PROJECT_NAME
TEST_DIR = f"{PROJECT_NAME}Tests"
BUNDLE_ID = "com.kvkleh.sbtsurvey"
DEPLOYMENT_TARGET = "16.0"
SWIFT_VERSION = "5.0"
MARKETING_VERSION = "1.0.0"

CAMERA_USAGE = (
    "Photographs of seabuckthorn shrubs are attached to survey records and stay "
    "on this device."
)
LOCATION_USAGE = (
    "Survey records store the plot coordinates, altitude and GPS accuracy. "
    "Positions stay on this device."
)
PHOTO_LIBRARY_USAGE = (
    "Used only when this device has no camera, so a shrub photograph can still be "
    "attached to a survey record."
)


def identifier(*parts: str) -> str:
    """A stable 24-character hex id, the shape Xcode uses for object keys."""
    digest = hashlib.sha256("::".join(parts).encode("utf-8")).hexdigest()
    return digest[:24].upper()


def quoted(value: str) -> str:
    """pbxproj strings only need quoting when they are not a bare word."""
    if value and all(character.isalnum() or character in "_./" for character in value):
        return value
    escaped = value.replace("\\", "\\\\").replace('"', '\\"')
    return f'"{escaped}"'


class Node:
    """A directory in the generated group tree."""

    def __init__(self, name: str, path: str):
        self.name = name
        self.path = path
        self.children: list[Node] = []
        self.files: list[str] = []


def collect(directory: str, relative: str = "") -> Node:
    """Walks a source directory, keeping Swift files and asset catalogues."""
    absolute = os.path.join(IOS_ROOT, directory, relative)
    node = Node(os.path.basename(relative) or directory, relative or directory)
    for entry in sorted(os.listdir(absolute)):
        if entry.startswith("."):
            continue
        entry_path = os.path.join(absolute, entry)
        entry_relative = os.path.join(relative, entry) if relative else entry
        if os.path.isdir(entry_path):
            if entry.endswith(".xcassets"):
                node.files.append(entry_relative)
            else:
                node.children.append(collect(directory, entry_relative))
        elif entry.endswith(".swift"):
            node.files.append(entry_relative)
    return node


def flatten(node: Node, directory: str) -> list[str]:
    """Every file in a tree, as paths relative to the ios/ folder."""
    found = [os.path.join(directory, name) for name in node.files]
    for child in node.children:
        found.extend(flatten(child, directory))
    return found


def group_objects(node: Node, directory: str, lines: list[str]) -> str:
    """Emits PBXGroup entries for a tree and returns the root group's id."""
    child_ids = [group_objects(child, directory, lines) for child in node.children]
    file_ids = [
        (identifier("file", os.path.join(directory, name)), os.path.basename(name))
        for name in node.files
    ]
    group_id = identifier("group", directory, node.path)
    children = child_ids + [file_id for file_id, _ in file_ids]

    lines.append(f"\t\t{group_id} = {{")
    lines.append("\t\t\tisa = PBXGroup;")
    lines.append("\t\t\tchildren = (")
    for child in children:
        lines.append(f"\t\t\t\t{child},")
    lines.append("\t\t\t);")
    lines.append(f"\t\t\tpath = {quoted(os.path.basename(node.path) or node.path)};")
    lines.append("\t\t\tsourceTree = \"<group>\";")
    lines.append("\t\t};")
    return group_id


def build_settings(settings: dict[str, str], indent: str) -> list[str]:
    return [f"{indent}{key} = {value};" for key, value in sorted(settings.items())]


def generate() -> str:
    app_tree = collect(APP_DIR)
    test_tree = collect(TEST_DIR)
    app_files = flatten(app_tree, APP_DIR)
    test_files = flatten(test_tree, TEST_DIR)

    app_sources = [f for f in app_files if f.endswith(".swift")]
    app_resources = [f for f in app_files if f.endswith(".xcassets")]
    test_sources = [f for f in test_files if f.endswith(".swift")]

    app_target = identifier("target", "app")
    test_target = identifier("target", "tests")
    project_id = identifier("project")
    main_group = identifier("group", "main")
    products_group = identifier("group", "products")
    app_product = identifier("product", "app")
    test_product = identifier("product", "tests")

    lines: list[str] = []
    lines.append("// !$*UTF8*$!")
    lines.append("{")
    lines.append("\tarchiveVersion = 1;")
    lines.append("\tclasses = {")
    lines.append("\t};")
    lines.append("\tobjectVersion = 56;")
    lines.append("\tobjects = {")

    # --- PBXBuildFile -----------------------------------------------------
    lines.append("\n/* Begin PBXBuildFile section */")
    for path in app_sources + app_resources + test_sources:
        lines.append(
            f"\t\t{identifier('build', path)} = {{isa = PBXBuildFile; "
            f"fileRef = {identifier('file', path)}; }};"
        )
    lines.append("/* End PBXBuildFile section */")

    # --- PBXFileReference -------------------------------------------------
    lines.append("\n/* Begin PBXFileReference section */")
    for path in app_files + test_files:
        name = os.path.basename(path)
        if name.endswith(".xcassets"):
            file_type = "folder.assetcatalog"
        else:
            file_type = "sourcecode.swift"
        lines.append(
            f"\t\t{identifier('file', path)} = {{isa = PBXFileReference; "
            f"lastKnownFileType = {file_type}; path = {quoted(name)}; "
            "sourceTree = \"<group>\"; };"
        )
    lines.append(
        f"\t\t{app_product} = {{isa = PBXFileReference; explicitFileType = "
        f"wrapper.application; includeInIndex = 0; path = {quoted(PROJECT_NAME + '.app')}; "
        "sourceTree = BUILT_PRODUCTS_DIR; };"
    )
    lines.append(
        f"\t\t{test_product} = {{isa = PBXFileReference; explicitFileType = "
        f"wrapper.cfbundle; includeInIndex = 0; path = {quoted(TEST_DIR + '.xctest')}; "
        "sourceTree = BUILT_PRODUCTS_DIR; };"
    )
    lines.append("/* End PBXFileReference section */")

    # --- PBXFrameworksBuildPhase -----------------------------------------
    lines.append("\n/* Begin PBXFrameworksBuildPhase section */")
    for name, target in (("app", app_target), ("tests", test_target)):
        lines.append(f"\t\t{identifier('frameworks', name)} = {{")
        lines.append("\t\t\tisa = PBXFrameworksBuildPhase;")
        lines.append("\t\t\tbuildActionMask = 2147483647;")
        lines.append("\t\t\tfiles = (")
        lines.append("\t\t\t);")
        lines.append("\t\t\trunOnlyForDeploymentPostprocessing = 0;")
        lines.append("\t\t};")
    lines.append("/* End PBXFrameworksBuildPhase section */")

    # --- PBXGroup ---------------------------------------------------------
    lines.append("\n/* Begin PBXGroup section */")
    app_group = group_objects(app_tree, APP_DIR, lines)
    test_group = group_objects(test_tree, TEST_DIR, lines)

    lines.append(f"\t\t{products_group} = {{")
    lines.append("\t\t\tisa = PBXGroup;")
    lines.append("\t\t\tchildren = (")
    lines.append(f"\t\t\t\t{app_product},")
    lines.append(f"\t\t\t\t{test_product},")
    lines.append("\t\t\t);")
    lines.append("\t\t\tname = Products;")
    lines.append("\t\t\tsourceTree = \"<group>\";")
    lines.append("\t\t};")

    lines.append(f"\t\t{main_group} = {{")
    lines.append("\t\t\tisa = PBXGroup;")
    lines.append("\t\t\tchildren = (")
    lines.append(f"\t\t\t\t{app_group},")
    lines.append(f"\t\t\t\t{test_group},")
    lines.append(f"\t\t\t\t{products_group},")
    lines.append("\t\t\t);")
    lines.append("\t\t\tsourceTree = \"<group>\";")
    lines.append("\t\t};")
    lines.append("/* End PBXGroup section */")

    # --- PBXNativeTarget --------------------------------------------------
    lines.append("\n/* Begin PBXNativeTarget section */")
    lines.append(f"\t\t{app_target} = {{")
    lines.append("\t\t\tisa = PBXNativeTarget;")
    lines.append(
        f"\t\t\tbuildConfigurationList = {identifier('configlist', 'app')};"
    )
    lines.append("\t\t\tbuildPhases = (")
    lines.append(f"\t\t\t\t{identifier('sources', 'app')},")
    lines.append(f"\t\t\t\t{identifier('frameworks', 'app')},")
    lines.append(f"\t\t\t\t{identifier('resources', 'app')},")
    lines.append("\t\t\t);")
    lines.append("\t\t\tbuildRules = (")
    lines.append("\t\t\t);")
    lines.append("\t\t\tdependencies = (")
    lines.append("\t\t\t);")
    lines.append(f"\t\t\tname = {PROJECT_NAME};")
    lines.append(f"\t\t\tproductName = {PROJECT_NAME};")
    lines.append(f"\t\t\tproductReference = {app_product};")
    lines.append("\t\t\tproductType = \"com.apple.product-type.application\";")
    lines.append("\t\t};")

    lines.append(f"\t\t{test_target} = {{")
    lines.append("\t\t\tisa = PBXNativeTarget;")
    lines.append(
        f"\t\t\tbuildConfigurationList = {identifier('configlist', 'tests')};"
    )
    lines.append("\t\t\tbuildPhases = (")
    lines.append(f"\t\t\t\t{identifier('sources', 'tests')},")
    lines.append(f"\t\t\t\t{identifier('frameworks', 'tests')},")
    lines.append("\t\t\t);")
    lines.append("\t\t\tbuildRules = (")
    lines.append("\t\t\t);")
    lines.append("\t\t\tdependencies = (")
    lines.append(f"\t\t\t\t{identifier('dependency', 'tests')},")
    lines.append("\t\t\t);")
    lines.append(f"\t\t\tname = {TEST_DIR};")
    lines.append(f"\t\t\tproductName = {TEST_DIR};")
    lines.append(f"\t\t\tproductReference = {test_product};")
    lines.append("\t\t\tproductType = \"com.apple.product-type.bundle.unit-test\";")
    lines.append("\t\t};")
    lines.append("/* End PBXNativeTarget section */")

    # --- PBXProject -------------------------------------------------------
    lines.append("\n/* Begin PBXProject section */")
    lines.append(f"\t\t{project_id} = {{")
    lines.append("\t\t\tisa = PBXProject;")
    lines.append("\t\t\tattributes = {")
    lines.append("\t\t\t\tBuildIndependentTargetsInParallel = 1;")
    lines.append("\t\t\t\tLastSwiftUpdateCheck = 1500;")
    lines.append("\t\t\t\tLastUpgradeCheck = 1500;")
    lines.append("\t\t\t\tTargetAttributes = {")
    lines.append(f"\t\t\t\t\t{app_target} = {{")
    lines.append("\t\t\t\t\t\tCreatedOnToolsVersion = 15.0;")
    lines.append("\t\t\t\t\t};")
    lines.append(f"\t\t\t\t\t{test_target} = {{")
    lines.append("\t\t\t\t\t\tCreatedOnToolsVersion = 15.0;")
    lines.append(f"\t\t\t\t\t\tTestTargetID = {app_target};")
    lines.append("\t\t\t\t\t};")
    lines.append("\t\t\t\t};")
    lines.append("\t\t\t};")
    lines.append(
        f"\t\t\tbuildConfigurationList = {identifier('configlist', 'project')};"
    )
    lines.append("\t\t\tcompatibilityVersion = \"Xcode 14.0\";")
    lines.append("\t\t\tdevelopmentRegion = en;")
    lines.append("\t\t\thasScannedForEncodings = 0;")
    lines.append("\t\t\tknownRegions = (")
    lines.append("\t\t\t\ten,")
    lines.append("\t\t\t\tBase,")
    lines.append("\t\t\t);")
    lines.append(f"\t\t\tmainGroup = {main_group};")
    lines.append(f"\t\t\tproductRefGroup = {products_group};")
    lines.append("\t\t\tprojectDirPath = \"\";")
    lines.append("\t\t\tprojectRoot = \"\";")
    lines.append("\t\t\ttargets = (")
    lines.append(f"\t\t\t\t{app_target},")
    lines.append(f"\t\t\t\t{test_target},")
    lines.append("\t\t\t);")
    lines.append("\t\t};")
    lines.append("/* End PBXProject section */")

    # --- PBXResourcesBuildPhase ------------------------------------------
    lines.append("\n/* Begin PBXResourcesBuildPhase section */")
    lines.append(f"\t\t{identifier('resources', 'app')} = {{")
    lines.append("\t\t\tisa = PBXResourcesBuildPhase;")
    lines.append("\t\t\tbuildActionMask = 2147483647;")
    lines.append("\t\t\tfiles = (")
    for path in app_resources:
        lines.append(f"\t\t\t\t{identifier('build', path)},")
    lines.append("\t\t\t);")
    lines.append("\t\t\trunOnlyForDeploymentPostprocessing = 0;")
    lines.append("\t\t};")
    lines.append("/* End PBXResourcesBuildPhase section */")

    # --- PBXSourcesBuildPhase --------------------------------------------
    lines.append("\n/* Begin PBXSourcesBuildPhase section */")
    for name, sources in (("app", app_sources), ("tests", test_sources)):
        lines.append(f"\t\t{identifier('sources', name)} = {{")
        lines.append("\t\t\tisa = PBXSourcesBuildPhase;")
        lines.append("\t\t\tbuildActionMask = 2147483647;")
        lines.append("\t\t\tfiles = (")
        for path in sources:
            lines.append(f"\t\t\t\t{identifier('build', path)},")
        lines.append("\t\t\t);")
        lines.append("\t\t\trunOnlyForDeploymentPostprocessing = 0;")
        lines.append("\t\t};")
    lines.append("/* End PBXSourcesBuildPhase section */")

    # --- PBXTargetDependency ---------------------------------------------
    lines.append("\n/* Begin PBXTargetDependency section */")
    lines.append(f"\t\t{identifier('dependency', 'tests')} = {{")
    lines.append("\t\t\tisa = PBXTargetDependency;")
    lines.append(f"\t\t\ttarget = {app_target};")
    lines.append(f"\t\t\ttargetProxy = {identifier('proxy', 'tests')};")
    lines.append("\t\t};")
    lines.append("/* End PBXTargetDependency section */")

    lines.append("\n/* Begin PBXContainerItemProxy section */")
    lines.append(f"\t\t{identifier('proxy', 'tests')} = {{")
    lines.append("\t\t\tisa = PBXContainerItemProxy;")
    lines.append(f"\t\t\tcontainerPortal = {project_id};")
    lines.append("\t\t\tproxyType = 1;")
    lines.append(f"\t\t\tremoteGlobalIDString = {app_target};")
    lines.append(f"\t\t\tremoteInfo = {PROJECT_NAME};")
    lines.append("\t\t};")
    lines.append("/* End PBXContainerItemProxy section */")

    # --- XCBuildConfiguration --------------------------------------------
    shared_project = {
        "ALWAYS_SEARCH_USER_PATHS": "NO",
        "CLANG_ANALYZER_NONNULL": "YES",
        "CLANG_ENABLE_MODULES": "YES",
        "CLANG_ENABLE_OBJC_ARC": "YES",
        "CLANG_WARN_DOCUMENTATION_COMMENTS": "YES",
        "CLANG_WARN_UNGUARDED_AVAILABILITY": "YES_AGGRESSIVE",
        "COPY_PHASE_STRIP": "NO",
        "ENABLE_STRICT_OBJC_MSGSEND": "YES",
        "GCC_NO_COMMON_BLOCKS": "YES",
        "IPHONEOS_DEPLOYMENT_TARGET": DEPLOYMENT_TARGET,
        "SDKROOT": "iphoneos",
        "SWIFT_VERSION": SWIFT_VERSION,
    }
    project_debug = dict(shared_project)
    project_debug.update({
        "DEBUG_INFORMATION_FORMAT": "dwarf",
        "ENABLE_TESTABILITY": "YES",
        "GCC_OPTIMIZATION_LEVEL": "0",
        "GCC_PREPROCESSOR_DEFINITIONS": '"DEBUG=1 $(inherited)"',
        "MTL_ENABLE_DEBUG_INFO": "INCLUDE_SOURCE",
        "ONLY_ACTIVE_ARCH": "YES",
        "SWIFT_ACTIVE_COMPILATION_CONDITIONS": '"DEBUG $(inherited)"',
        "SWIFT_OPTIMIZATION_LEVEL": '"-Onone"',
    })
    project_release = dict(shared_project)
    project_release.update({
        "DEBUG_INFORMATION_FORMAT": '"dwarf-with-dsym"',
        "ENABLE_NS_ASSERTIONS": "NO",
        "MTL_ENABLE_DEBUG_INFO": "NO",
        "SWIFT_COMPILATION_MODE": "wholemodule",
        "VALIDATE_PRODUCT": "YES",
    })

    app_settings = {
        "ASSETCATALOG_COMPILER_APPICON_NAME": "AppIcon",
        "ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME": "AccentColor",
        "CODE_SIGN_STYLE": "Automatic",
        "CURRENT_PROJECT_VERSION": "1",
        "ENABLE_PREVIEWS": "YES",
        "GENERATE_INFOPLIST_FILE": "YES",
        "INFOPLIST_KEY_CFBundleDisplayName": quoted("SBT Survey"),
        "INFOPLIST_KEY_NSCameraUsageDescription": quoted(CAMERA_USAGE),
        "INFOPLIST_KEY_NSLocationWhenInUseUsageDescription": quoted(LOCATION_USAGE),
        "INFOPLIST_KEY_NSPhotoLibraryUsageDescription": quoted(PHOTO_LIBRARY_USAGE),
        "INFOPLIST_KEY_UIApplicationSceneManifest_Generation": "YES",
        "INFOPLIST_KEY_UIApplicationSupportsIndirectInputEvents": "YES",
        "INFOPLIST_KEY_UILaunchScreen_Generation": "YES",
        "INFOPLIST_KEY_UIStatusBarStyle": "UIStatusBarStyleDefault",
        "INFOPLIST_KEY_UISupportedInterfaceOrientations_iPad": quoted(
            "UIInterfaceOrientationPortrait UIInterfaceOrientationPortraitUpsideDown "
            "UIInterfaceOrientationLandscapeLeft UIInterfaceOrientationLandscapeRight"
        ),
        "INFOPLIST_KEY_UISupportedInterfaceOrientations_iPhone": quoted(
            "UIInterfaceOrientationPortrait"
        ),
        "LD_RUNPATH_SEARCH_PATHS": '"$(inherited) @executable_path/Frameworks"',
        "MARKETING_VERSION": MARKETING_VERSION,
        "OTHER_LDFLAGS": '"-lsqlite3"',
        "PRODUCT_BUNDLE_IDENTIFIER": BUNDLE_ID,
        "PRODUCT_NAME": '"$(TARGET_NAME)"',
        "SWIFT_EMIT_LOC_STRINGS": "YES",
        "TARGETED_DEVICE_FAMILY": '"1,2"',
    }

    test_settings = {
        "BUNDLE_LOADER": '"$(TEST_HOST)"',
        "CODE_SIGN_STYLE": "Automatic",
        "CURRENT_PROJECT_VERSION": "1",
        "GENERATE_INFOPLIST_FILE": "YES",
        "MARKETING_VERSION": MARKETING_VERSION,
        "PRODUCT_BUNDLE_IDENTIFIER": f"{BUNDLE_ID}.tests",
        "PRODUCT_NAME": '"$(TARGET_NAME)"',
        "TARGETED_DEVICE_FAMILY": '"1,2"',
        "TEST_HOST": (
            f'"$(BUILT_PRODUCTS_DIR)/{PROJECT_NAME}.app/'
            f'$(BUNDLE_EXECUTABLE_FOLDER_PATH)/{PROJECT_NAME}"'
        ),
    }

    lines.append("\n/* Begin XCBuildConfiguration section */")
    for scope, configurations in (
        ("project", {"Debug": project_debug, "Release": project_release}),
        ("app", {"Debug": app_settings, "Release": app_settings}),
        ("tests", {"Debug": test_settings, "Release": test_settings}),
    ):
        for configuration, settings in configurations.items():
            lines.append(f"\t\t{identifier('config', scope, configuration)} = {{")
            lines.append("\t\t\tisa = XCBuildConfiguration;")
            lines.append("\t\t\tbuildSettings = {")
            lines.extend(build_settings(settings, "\t\t\t\t"))
            lines.append("\t\t\t};")
            lines.append(f"\t\t\tname = {configuration};")
            lines.append("\t\t};")
    lines.append("/* End XCBuildConfiguration section */")

    # --- XCConfigurationList ---------------------------------------------
    lines.append("\n/* Begin XCConfigurationList section */")
    for scope in ("project", "app", "tests"):
        lines.append(f"\t\t{identifier('configlist', scope)} = {{")
        lines.append("\t\t\tisa = XCConfigurationList;")
        lines.append("\t\t\tbuildConfigurations = (")
        lines.append(f"\t\t\t\t{identifier('config', scope, 'Debug')},")
        lines.append(f"\t\t\t\t{identifier('config', scope, 'Release')},")
        lines.append("\t\t\t);")
        lines.append("\t\t\tdefaultConfigurationIsVisible = 0;")
        lines.append("\t\t\tdefaultConfigurationName = Release;")
        lines.append("\t\t};")
    lines.append("/* End XCConfigurationList section */")

    lines.append("\t};")
    lines.append(f"\trootObject = {project_id};")
    lines.append("}")
    return "\n".join(lines) + "\n"


SCHEME_TEMPLATE = """<?xml version="1.0" encoding="UTF-8"?>
<Scheme
   LastUpgradeVersion = "1500"
   version = "1.7">
   <BuildAction
      parallelizeBuildables = "YES"
      buildImplicitDependencies = "YES">
      <BuildActionEntries>
         <BuildActionEntry
            buildForTesting = "YES"
            buildForRunning = "YES"
            buildForProfiling = "YES"
            buildForArchiving = "YES"
            buildForAnalyzing = "YES">
            <BuildableReference
               BuildableIdentifier = "primary"
               BlueprintIdentifier = "{app_target}"
               BuildableName = "{project}.app"
               BlueprintName = "{project}"
               ReferencedContainer = "container:{project}.xcodeproj">
            </BuildableReference>
         </BuildActionEntry>
      </BuildActionEntries>
   </BuildAction>
   <TestAction
      buildConfiguration = "Debug"
      selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB"
      selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB"
      shouldUseLaunchSchemeArgsEnv = "YES">
      <Testables>
         <TestableReference
            skipped = "NO">
            <BuildableReference
               BuildableIdentifier = "primary"
               BlueprintIdentifier = "{test_target}"
               BuildableName = "{project}Tests.xctest"
               BlueprintName = "{project}Tests"
               ReferencedContainer = "container:{project}.xcodeproj">
            </BuildableReference>
         </TestableReference>
      </Testables>
   </TestAction>
   <LaunchAction
      buildConfiguration = "Debug"
      selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB"
      selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB"
      launchStyle = "0"
      useCustomWorkingDirectory = "NO"
      ignoresPersistentStateOnLaunch = "NO"
      debugDocumentVersioning = "YES"
      debugServiceExtension = "internal"
      allowLocationSimulation = "YES">
      <BuildableProductRunnable
         runnableDebuggingMode = "0">
         <BuildableReference
            BuildableIdentifier = "primary"
            BlueprintIdentifier = "{app_target}"
            BuildableName = "{project}.app"
            BlueprintName = "{project}"
            ReferencedContainer = "container:{project}.xcodeproj">
         </BuildableReference>
      </BuildableProductRunnable>
   </LaunchAction>
   <ProfileAction
      buildConfiguration = "Release"
      shouldUseLaunchSchemeArgsEnv = "YES"
      savedToolIdentifier = ""
      useCustomWorkingDirectory = "NO"
      debugDocumentVersioning = "YES">
      <BuildableProductRunnable
         runnableDebuggingMode = "0">
         <BuildableReference
            BuildableIdentifier = "primary"
            BlueprintIdentifier = "{app_target}"
            BuildableName = "{project}.app"
            BlueprintName = "{project}"
            ReferencedContainer = "container:{project}.xcodeproj">
         </BuildableReference>
      </BuildableProductRunnable>
   </ProfileAction>
   <AnalyzeAction
      buildConfiguration = "Debug">
   </AnalyzeAction>
   <ArchiveAction
      buildConfiguration = "Release"
      revealArchiveInOrganizer = "YES">
   </ArchiveAction>
</Scheme>
"""


def main() -> None:
    project_dir = os.path.join(IOS_ROOT, f"{PROJECT_NAME}.xcodeproj")
    if os.path.isdir(project_dir):
        shutil.rmtree(project_dir)
    scheme_dir = os.path.join(project_dir, "xcshareddata", "xcschemes")
    os.makedirs(scheme_dir)

    with open(os.path.join(project_dir, "project.pbxproj"), "w") as handle:
        handle.write(generate())

    with open(os.path.join(scheme_dir, f"{PROJECT_NAME}.xcscheme"), "w") as handle:
        handle.write(
            SCHEME_TEMPLATE.format(
                project=PROJECT_NAME,
                app_target=identifier("target", "app"),
                test_target=identifier("target", "tests"),
            )
        )
    print(f"wrote {project_dir}")


if __name__ == "__main__":
    main()
