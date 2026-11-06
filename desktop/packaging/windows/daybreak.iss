; One-click installer of the Daybreak desktop app (Inno Setup 6).
;
; Double-click: no pages, no questions. The setup shows a small window with the Daybreak logo and a
; progress bar, installs for the current user (no administrator rights, no UAC prompt), adds the
; Start menu entry, registers an uninstaller, closes a running Daybreak, replaces an older version
; in place and starts the app when it is done.
;
; Switches (the in-app updater uses the first line):
;   /SILENT /SUPPRESSMSGBOXES /NORESTART   progress window only, never asks (in-app update)
;   /VERYSILENT                            no window at all
;   /TASKS="desktopicon"                   also put a shortcut on the desktop (kept on later updates)
;   /DIR="C:\path"                         another install folder (default %LOCALAPPDATA%\Programs\Daybreak)
;   /SKIPMSI=1                             do not look for the old MSI install (for testing)
;
; Built by the Gradle task :desktop:windowsInstaller, which passes
;   /DAppVersion=1.2.3 /DSourceDir=<app image of createDistributable> /DOutputDir=<folder>
; An older MSI install (the previous installer, per machine in Program Files) is removed first;
; Windows asks for administrator approval once for that, since the MSI lives in Program Files.

#ifndef AppVersion
  #define AppVersion "1.0.0"
#endif
#ifndef SourceDir
  #error SourceDir is not set. Run ./gradlew.bat :desktop:windowsInstaller instead of compiling this file by hand.
#endif
#ifndef OutputDir
  #define OutputDir "."
#endif

#define AppName "Daybreak"
#define AppExe "Daybreak.exe"

[Setup]
; Fixed for this product: a newer setup finds the install of an older one through it.
AppId={{D4B1E7A0-5C3F-4E8B-9A62-7F1D0C9E3B58}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher=BDER Solutions
AppPublisherURL=https://github.com/blutuxyz/daybreak
AppSupportURL=https://github.com/blutuxyz/daybreak/issues
VersionInfoVersion={#AppVersion}
VersionInfoProductName={#AppName}
VersionInfoDescription=Daybreak Setup
DefaultDirName={localappdata}\Programs\{#AppName}
UsePreviousAppDir=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
; No pages except the progress window: nothing to click through.
DisableWelcomePage=yes
DisableDirPage=yes
DisableProgramGroupPage=yes
DisableReadyPage=yes
DisableFinishedPage=yes
DisableStartupPrompt=yes
ShowLanguageDialog=no
LanguageDetectionMethod=uilanguage
; A running Daybreak is closed (without asking) and the new files replace the old ones.
CloseApplications=force
RestartApplications=no
SetupIconFile=..\daybreak.ico
UninstallDisplayIcon={app}\{#AppExe}
UninstallDisplayName={#AppName}
WizardStyle=modern
WizardSmallImageFile=wizard-small-55.bmp,wizard-small-64.bmp,wizard-small-80.bmp,wizard-small-96.bmp,wizard-small-110.bmp
OutputDir={#OutputDir}
OutputBaseFilename={#AppName}-{#AppVersion}-setup
Compression=lzma2/max
SolidCompression=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "german"; MessagesFile: "compiler:Languages\German.isl"

[Tasks]
; Not offered on a page (there is none); /TASKS="desktopicon" turns it on.
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; Flags: unchecked

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[InstallDelete]
; A new version may drop files of the old one (jars, runtime modules); start from clean folders.
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"

[Icons]
Name: "{userprograms}\{#AppName}"; Filename: "{app}\{#AppExe}"
; The desktop shortcut is made when asked for, and kept when it already exists (updates).
Name: "{userdesktop}\{#AppName}"; Filename: "{app}\{#AppExe}"; Check: WantsDesktopShortcut

[Run]
; Without the postinstall flag this runs after every install, silent or not (the in-app update
; relies on it to bring the new version up).
Filename: "{app}\{#AppExe}"; Flags: nowait runasoriginaluser skipifdoesntexist

[UninstallDelete]
; Whatever the app left next to its files. The settings and the timetable cache live in
; %APPDATA%\Daybreak and stay, so a reinstall finds the person still signed in.
Type: filesandordirs; Name: "{app}"

[Code]
function WantsDesktopShortcut: Boolean;
begin
  Result := WizardIsTaskSelected('desktopicon') or FileExists(ExpandConstant('{userdesktop}\{#AppName}.lnk'));
end;

procedure CloseRunningDaybreak;
var
  ResultCode: Integer;
begin
  { Restart Manager (CloseApplications) handles the install folder; this also catches a copy that
    runs from the old MSI folder or from the portable ZIP, whose files setup does not touch. }
  Exec(ExpandConstant('{sys}\taskkill.exe'), '/F /T /IM {#AppExe}', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
end;

{ Looks for the entry the old MSI installer (jpackage/WiX) left in one uninstall branch. Its key is
  the product code in braces; Inno Setup's own entry ends in _is1 and has no WindowsInstaller value. }
function FindMsiIn(RootKey: Integer; var ProductCode: String): Boolean;
var
  Base, Name, Value: String;
  Keys: TArrayOfString;
  Flag: Cardinal;
  I: Integer;
begin
  Result := False;
  Base := 'Software\Microsoft\Windows\CurrentVersion\Uninstall';
  if not RegGetSubkeyNames(RootKey, Base, Keys) then Exit;
  for I := 0 to GetArrayLength(Keys) - 1 do
  begin
    Name := Keys[I];
    if (Length(Name) < 3) or (Name[1] <> '{') then Continue;
    if not RegQueryDWordValue(RootKey, Base + '\' + Name, 'WindowsInstaller', Flag) or (Flag <> 1) then Continue;
    if not RegQueryStringValue(RootKey, Base + '\' + Name, 'DisplayName', Value) or (Value <> '{#AppName}') then Continue;
    if not RegQueryStringValue(RootKey, Base + '\' + Name, 'Publisher', Value) or (Value <> 'BDER Solutions') then Continue;
    ProductCode := Name;
    Result := True;
    Exit;
  end;
end;

{ Removes the Daybreak that the old MSI installer put in Program Files, so the two never exist side
  by side. A per-machine MSI needs administrator rights: Windows shows its approval prompt once. If it
  is declined, setup carries on and installs for the user anyway. }
procedure RemoveOldMsi;
var
  ProductCode: String;
  ResultCode, Round: Integer;
  Machine: Boolean;
begin
  if ExpandConstant('{param:SKIPMSI|0}') = '1' then Exit;
  for Round := 1 to 3 do
  begin
    Machine := FindMsiIn(HKLM64, ProductCode) or FindMsiIn(HKLM32, ProductCode);
    if not Machine and not FindMsiIn(HKCU, ProductCode) then Exit;
    Log('Removing the old MSI install ' + ProductCode);
    if Machine then
    begin
      if not ShellExec('runas', 'msiexec.exe', '/x ' + ProductCode + ' /passive /norestart', '', SW_SHOWNORMAL, ewWaitUntilTerminated, ResultCode) then
      begin
        Log('The administrator prompt was declined; the old install stays.');
        Exit;
      end;
    end
    else
      Exec('msiexec.exe', '/x ' + ProductCode + ' /passive /norestart', '', SW_SHOWNORMAL, ewWaitUntilTerminated, ResultCode);
    Log('msiexec ended with ' + IntToStr(ResultCode));
    if (ResultCode <> 0) and (ResultCode <> 3010) then Exit;
  end;
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  CloseRunningDaybreak;
  RemoveOldMsi;
  Result := '';
end;

function InitializeUninstall: Boolean;
begin
  CloseRunningDaybreak;
  Result := True;
end;

{ The task page would be the only page left. Nothing to ask: skip it. The desktop shortcut is chosen
  with /TASKS="desktopicon" and then stays on every update (WantsDesktopShortcut). }
function ShouldSkipPage(PageID: Integer): Boolean;
begin
  Result := PageID = wpSelectTasks;
end;

{ Setup keeps the ready page ("Install" button) even with DisableReadyPage=yes and a skip request, so
  the button is pressed for the person as soon as the page is up and they only see the progress window.
  The click is posted, not made directly: a click inside CurPageChanged is lost, the page change is
  still running. }
function PostMessage(Window: HWND; Msg: UINT; WParam: LongInt; LParam: LongInt): BOOL;
  external 'PostMessageW@user32.dll stdcall';

procedure CurPageChanged(CurPageID: Integer);
begin
  if CurPageID = wpReady then
    PostMessage(WizardForm.NextButton.Handle, $00F5 { BM_CLICK }, 0, 0);
end;
