#!/usr/bin/env python3
"""Generate reproducible dependency-free multiplatform Xcode project. Signing stays unconfigured."""
from pathlib import Path
import json, shutil, plistlib
ROOT = Path(__file__).resolve().parents[1]
APPLE = ROOT / 'apple'
APPLE.mkdir(exist_ok=True)
project = APPLE / 'CameraStats.xcodeproj'
project.mkdir(exist_ok=True)
assets = APPLE / 'Assets.xcassets'
logo = assets / 'SPMark.imageset'
logo.mkdir(parents=True, exist_ok=True)
shutil.copyfile(ROOT / 'assets/brand/SP_GREEN.png', logo / 'SP_GREEN.png')
(assets / 'Contents.json').write_text(json.dumps({'info':{'author':'xcode','version':1}}))
(logo / 'Contents.json').write_text(json.dumps({'images':[{'filename':'SP_GREEN.png','idiom':'universal'}],'info':{'author':'xcode','version':1}}))
for name,light,dark in [('Canvas','FAF8F9','191419'),('Paper','FFFFFF','251D23'),('Ink','241D21','F3EAF0'),('AccentColor','237B20','95DF8D')]:
    folder=assets/(name+'.colorset'); folder.mkdir(exist_ok=True)
    def color(hexvalue):
        components = {c:f'{int(hexvalue[i:i+2],16)/255:.6f}' for c,i in [('red',0),('green',2),('blue',4)]}
        components['alpha'] = '1.000'
        return {'color-space':'srgb','components':components}
    (folder/'Contents.json').write_text(json.dumps({'colors':[{'idiom':'universal','color':color(light)},{'idiom':'universal','appearances':[{'appearance':'luminosity','value':'dark'}],'color':color(dark)}],'info':{'author':'xcode','version':1}}))
info = {'CFBundleDisplayName':'Shot Reporter','CFBundleName':'Shot Reporter','CFBundleIdentifier':'$(PRODUCT_BUNDLE_IDENTIFIER)','CFBundleExecutable':'$(EXECUTABLE_NAME)','CFBundlePackageType':'APPL','CFBundleShortVersionString':'0.1.1','CFBundleVersion':'2','ITSAppUsesNonExemptEncryption':False,'LSApplicationCategoryType':'public.app-category.productivity','UILaunchScreen':{},'UIApplicationSceneManifest':{'UIApplicationSupportsMultipleScenes':False},'UISupportedInterfaceOrientations':['UIInterfaceOrientationPortrait','UIInterfaceOrientationLandscapeLeft','UIInterfaceOrientationLandscapeRight'],'UISupportedInterfaceOrientations~ipad':['UIInterfaceOrientationPortrait','UIInterfaceOrientationPortraitUpsideDown','UIInterfaceOrientationLandscapeLeft','UIInterfaceOrientationLandscapeRight'],'UTExportedTypeDeclarations':[{'UTTypeIdentifier':'org.squidproductions.camreport','UTTypeDescription':'Shot Reporter Report','UTTypeConformsTo':['public.data'],'UTTypeTagSpecification':{'public.filename-extension':['camreport']}}],'CFBundleDocumentTypes':[{'CFBundleTypeName':'Shot Reporter Report','LSItemContentTypes':['org.squidproductions.camreport'],'LSHandlerRank':'Owner'}],'UIFileSharingEnabled':True,'LSSupportsOpeningDocumentsInPlace':True}
(APPLE / 'Info.plist').write_bytes(plistlib.dumps(info))
(APPLE / 'CameraStats.entitlements').write_bytes(plistlib.dumps({'com.apple.security.app-sandbox':True,'com.apple.security.files.user-selected.read-write':True}))
(APPLE / 'PrivacyInfo.xcprivacy').write_bytes(plistlib.dumps({'NSPrivacyTracking':False,'NSPrivacyCollectedDataTypes':[],'NSPrivacyAccessedAPITypes':[{'NSPrivacyAccessedAPIType':'NSPrivacyAccessedAPICategoryUserDefaults','NSPrivacyAccessedAPITypeReasons':['CA92.1']}]}))
def ident(n): return f'{n:024X}'
objects=[]
def obj(n,s): objects.append(f'{ident(n)} = {{ {s} }};')
for i,name in enumerate(['CameraStatsApp.swift','Store.swift'],10):
    obj(i,f'isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = "../app/Sources/CameraStats/{name}"; sourceTree = "<group>";')
    obj(i+10,f'isa = PBXBuildFile; fileRef = {ident(i)};')
obj(30,'isa = PBXFileReference; lastKnownFileType = folder.assetcatalog; path = Assets.xcassets; sourceTree = "<group>";')
obj(31,f'isa = PBXBuildFile; fileRef = {ident(30)};')
obj(32,'isa = PBXFileReference; lastKnownFileType = text.xml; path = PrivacyInfo.xcprivacy; sourceTree = "<group>";')
obj(33,f'isa = PBXBuildFile; fileRef = {ident(32)};')
obj(40,f'isa = PBXGroup; children = ({ident(10)},{ident(11)},{ident(30)},{ident(32)},{ident(41)}); sourceTree = "<group>";')
obj(41,f'isa = PBXGroup; children = ({ident(42)}); name = Products; sourceTree = "<group>";')
obj(42,'isa = PBXFileReference; explicitFileType = wrapper.application; path = "Shot Reporter.app"; sourceTree = BUILT_PRODUCTS_DIR;')
obj(50,f'isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; files = ({ident(20)},{ident(21)}); runOnlyForDeploymentPostprocessing = 0;')
obj(51,f'isa = PBXFrameworksBuildPhase; buildActionMask = 2147483647; files = ({ident(82)}); runOnlyForDeploymentPostprocessing = 0;')
obj(52,f'isa = PBXResourcesBuildPhase; buildActionMask = 2147483647; files = ({ident(31)},{ident(33)}); runOnlyForDeploymentPostprocessing = 0;')
obj(60,f'isa = PBXNativeTarget; buildConfigurationList = {ident(71)}; buildPhases = ({ident(50)},{ident(51)},{ident(52)}); buildRules = (); dependencies = (); name = CameraStats; packageProductDependencies = ({ident(81)}); productName = CameraStats; productReference = {ident(42)}; productType = "com.apple.product-type.application";')
obj(61,f'isa = PBXProject; attributes = {{ BuildIndependentTargetsInParallel = YES; LastUpgradeCheck = 2660; }}; buildConfigurationList = {ident(70)}; compatibilityVersion = "Xcode 14.0"; developmentRegion = en; knownRegions = (en,Base); mainGroup = {ident(40)}; productRefGroup = {ident(41)}; packageReferences = ({ident(80)}); projectDirPath = ""; projectRoot = ""; targets = ({ident(60)});')
obj(80,'isa = XCLocalSwiftPackageReference; relativePath = ../app;')
obj(81,f'isa = XCSwiftPackageProductDependency; package = {ident(80)}; productName = CameraStatsCore;')
obj(82,f'isa = PBXBuildFile; productRef = {ident(81)};')
for n,configs in [(70,(72,73)),(71,(74,75))]: obj(n,f'isa = XCConfigurationList; buildConfigurations = ({",".join(ident(x) for x in configs)}); defaultConfigurationIsVisible = 0; defaultConfigurationName = Release;')
for n,name in [(72,'Debug'),(73,'Release')]:
    obj(n,f'isa = XCBuildConfiguration; buildSettings = {{ CLANG_ENABLE_MODULES = YES; SWIFT_VERSION = 5.0; ONLY_ACTIVE_ARCH = {"YES" if name == "Debug" else "NO"}; SWIFT_OPTIMIZATION_LEVEL = "{"-Onone" if name == "Debug" else "-O"}"; }}; name = {name};')
for n,name in [(74,'Debug'),(75,'Release')]:
    settings = 'CODE_SIGN_STYLE = Automatic; DEVELOPMENT_TEAM = ""; PRODUCT_BUNDLE_IDENTIFIER = org.squidproductions.camerastats; PRODUCT_NAME = "Shot Reporter"; GENERATE_INFOPLIST_FILE = NO; INFOPLIST_FILE = Info.plist; MARKETING_VERSION = 0.1.1; CURRENT_PROJECT_VERSION = 2; DEBUG_INFORMATION_FORMAT = "dwarf-with-dsym"; IPHONEOS_DEPLOYMENT_TARGET = 17.0; MACOSX_DEPLOYMENT_TARGET = 14.0; TARGETED_DEVICE_FAMILY = "1,2"; SUPPORTED_PLATFORMS = "macosx iphoneos iphonesimulator"; SDKROOT = auto; SUPPORTS_MACCATALYST = NO; ENABLE_APP_SANDBOX = YES; ENABLE_HARDENED_RUNTIME = YES; "CODE_SIGN_ENTITLEMENTS[sdk=macosx*]" = CameraStats.entitlements; ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;'
    obj(n,f'isa = XCBuildConfiguration; buildSettings = {{ {settings} }}; name = {name};')
(project / 'project.pbxproj').write_text('// !$*UTF8*$!\n{ archiveVersion = 1; classes = {}; objectVersion = 56; objects = {\n'+'\n'.join(objects)+f'\n}}; rootObject = {ident(61)}; }}\n')
schemes=project/'xcshareddata/xcschemes'; schemes.mkdir(parents=True,exist_ok=True)
ref=f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{ident(60)}" BuildableName="Shot Reporter.app" BlueprintName="CameraStats" ReferencedContainer="container:CameraStats.xcodeproj"/>'
(schemes/'CameraStats.xcscheme').write_text(f'<?xml version="1.0" encoding="UTF-8"?><Scheme LastUpgradeVersion="2660" version="1.3"><BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">{ref}</BuildActionEntry></BuildActionEntries></BuildAction><LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO"><BuildableProductRunnable runnableDebuggingMode="0">{ref}</BuildableProductRunnable></LaunchAction><ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/></Scheme>')
print(project)
