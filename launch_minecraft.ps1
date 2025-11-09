# Script for automatic Minecraft launch via TLauncher
$maxAttempts = 40
$attempt = 0
$windowFound = $false
$process = $null

Write-Host "Searching for TLauncher window..."

while ($attempt -lt $maxAttempts -and -not $windowFound) {
    $processes = Get-Process | Where-Object { 
        ($_.MainWindowTitle -like "*TLauncher*" -or $_.ProcessName -like "*tlauncher*") -and 
        $_.MainWindowTitle -ne "" 
    }
    
    if ($processes) {
        foreach ($proc in $processes) {
            if ($proc.MainWindowHandle -ne [IntPtr]::Zero) {
                $windowFound = $true
                $process = $proc
                Write-Host "TLauncher window found: $($proc.MainWindowTitle)"
                break
            }
        }
    }
    
    if (-not $windowFound) {
        $attempt++
        Start-Sleep -Milliseconds 500
    }
}

if (-not $windowFound) {
    Write-Host "TLauncher window not found after $maxAttempts attempts"
    exit 1
}

Write-Host "Waiting for window to fully load (10 seconds)..."
Start-Sleep -Seconds 10

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win32 {
    [DllImport("user32.dll")]
    public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")]
    public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")]
    public static extern bool BringWindowToTop(IntPtr hWnd);
    [DllImport("user32.dll")]
    public static extern bool IsIconic(IntPtr hWnd);
    [DllImport("user32.dll")]
    public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")]
    public static extern bool AttachThreadInput(uint idAttach, uint idAttachTo, bool fAttach);
    [DllImport("kernel32.dll")]
    public static extern uint GetCurrentThreadId();
    [DllImport("user32.dll")]
    public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);
    [StructLayout(LayoutKind.Sequential)]
    public struct RECT {
        public int Left;
        public int Top;
        public int Right;
        public int Bottom;
    }
    [DllImport("user32.dll")]
    public static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);
    [DllImport("user32.dll")]
    public static extern int SetCursorPos(int X, int Y);
    [DllImport("user32.dll")]
    public static extern void mouse_event(uint dwFlags, uint dx, uint dy, uint dwData, IntPtr dwExtraInfo);
    public static readonly int SW_RESTORE = 9;
    public static readonly int SW_SHOW = 5;
    public static readonly uint MOUSEEVENTF_LEFTDOWN = 0x0002;
    public static readonly uint MOUSEEVENTF_LEFTUP = 0x0004;
}
"@

Write-Host "Activating TLauncher window..."

$windowHandle = $process.MainWindowHandle

if ([Win32]::IsIconic($windowHandle)) {
    [Win32]::ShowWindow($windowHandle, [Win32]::SW_RESTORE)
    Start-Sleep -Milliseconds 500
}

$currentThreadId = [Win32]::GetCurrentThreadId()
$windowThreadId = [Win32]::GetWindowThreadProcessId($windowHandle, [ref]0)

if ($windowThreadId -ne $currentThreadId) {
    [Win32]::AttachThreadInput($currentThreadId, $windowThreadId, $true)
}

[Win32]::ShowWindow($windowHandle, [Win32]::SW_SHOW)
[Win32]::BringWindowToTop($windowHandle)
[Win32]::SetForegroundWindow($windowHandle)

if ($windowThreadId -ne $currentThreadId) {
    [Win32]::AttachThreadInput($currentThreadId, $windowThreadId, $false)
}

Start-Sleep -Milliseconds 1000

# Try multiple methods to get window position
$rect = New-Object Win32+RECT
$gotRect = [Win32]::GetWindowRect($windowHandle, [ref]$rect)

if ($gotRect) {
    $width = $rect.Right - $rect.Left
    $height = $rect.Bottom - $rect.Top
    Write-Host "Window position: Left=$($rect.Left), Top=$($rect.Top), Width=$width, Height=$height"
    
    # Button "Войти в игру" position based on full screen screenshot:
    # Кнопка находится в правом нижнем углу окна, левее кнопки "TL MODS"
    # Точные координаты: 72% ширины, 93% высоты от левого верхнего угла окна
    $buttonX = $rect.Left + ($width * 0.72)
    $buttonY = $rect.Top + ($height * 0.93)
    
    Write-Host "Clicking at button position: ($buttonX, $buttonY) - 72% width, 93% height"
    [Win32]::SetCursorPos([int]$buttonX, [int]$buttonY)
    Start-Sleep -Milliseconds 200
    [Win32]::mouse_event([Win32]::MOUSEEVENTF_LEFTDOWN, 0, 0, 0, [IntPtr]::Zero)
    Start-Sleep -Milliseconds 100
    [Win32]::mouse_event([Win32]::MOUSEEVENTF_LEFTUP, 0, 0, 0, [IntPtr]::Zero)
    Write-Host "Mouse click sent"
} else {
    # Alternative: Use UI Automation to get window bounds
    Write-Host "GetWindowRect failed, trying UI Automation..."
    Add-Type -AssemblyName UIAutomationClient
    Add-Type -AssemblyName UIAutomationTypes
    
    try {
        $root = [System.Windows.Automation.AutomationElement]::RootElement
        $windowCondition = New-Object System.Windows.Automation.PropertyCondition(
            [System.Windows.Automation.AutomationElement]::NameProperty,
            "TLauncher"
        )
        
        $tlauncherWindow = $root.FindFirst([System.Windows.Automation.TreeScope]::Children, $windowCondition)
        
        if ($tlauncherWindow -eq $null) {
            # Try alternative search
            $allWindows = $root.FindAll([System.Windows.Automation.TreeScope]::Children, 
                [System.Windows.Automation.Condition]::TrueCondition)
            
            foreach ($win in $allWindows) {
                $name = $win.Current.Name
                if ($name -like "*TLauncher*" -or $name -like "*TLAUNCHER*") {
                    $tlauncherWindow = $win
                    break
                }
            }
        }
        
        if ($tlauncherWindow -ne $null) {
            $bounds = $tlauncherWindow.Current.BoundingRectangle
            $width = $bounds.Width
            $height = $bounds.Height
            $left = $bounds.Left
            $top = $bounds.Top
            
            Write-Host "Window position (via UI Automation): Left=$left, Top=$top, Width=$width, Height=$height"
            
            # Button "Войти в игру" position based on full screen screenshot:
            # Кнопка находится в правом нижнем углу окна, левее кнопки "TL MODS"
            # Точные координаты: 72% ширины, 93% высоты от левого верхнего угла окна
            $buttonX = $left + ($width * 0.72)
            $buttonY = $top + ($height * 0.93)
            
            Write-Host "Clicking at button position: ($buttonX, $buttonY) - 72% width, 93% height"
            [Win32]::SetCursorPos([int]$buttonX, [int]$buttonY)
            Start-Sleep -Milliseconds 200
            [Win32]::mouse_event([Win32]::MOUSEEVENTF_LEFTDOWN, 0, 0, 0, [IntPtr]::Zero)
            Start-Sleep -Milliseconds 100
            [Win32]::mouse_event([Win32]::MOUSEEVENTF_LEFTUP, 0, 0, 0, [IntPtr]::Zero)
            Write-Host "Mouse click sent"
            exit 0
        } else {
            Write-Host "ERROR: Could not find TLauncher window via UI Automation"
            exit 1
        }
    } catch {
        Write-Host "ERROR: Could not get window info via UI Automation: $_"
        exit 1
    }
}

exit 0
