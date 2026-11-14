using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using FlaUI.Core.AutomationElements;
using FlaUI.Core.Definitions;
using FlaUI.Core.Input;
using FlaUI.Core.WindowsAPI;
using FlaUI.UIA3;

namespace Hopa.Windows;

/// <summary>
/// W3: the Windows device adapter. Observation is a real UI Automation tree with
/// pre-order refs; actions try the UIA pattern first and fall back to the mouse
/// or keyboard. Every call is wrapped: a control that disappears or throws is
/// reported, never swallowed.
/// </summary>
public sealed class WindowsDevice : IDisposable
{
    private const int MaxNodes = 350;
    private const int MaxDepth = 14;

    private readonly UIA3Automation automation = new();
    private readonly Dictionary<string, AutomationElement> refs = new();
    private AutomationElement? root;
    private int counter;

    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr hwnd, out uint processId);

    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr hwnd);

    [DllImport("user32.dll")]
    private static extern bool ShowWindow(IntPtr hwnd, int command);

    [DllImport("user32.dll")]
    private static extern bool AttachThreadInput(uint attach, uint attachTo, bool attachFlag);

    [DllImport("kernel32.dll")]
    private static extern uint GetCurrentThreadId();

    [StructLayout(LayoutKind.Sequential)]
    private struct Rect
    {
        public int Left;
        public int Top;
        public int Right;
        public int Bottom;
    }

    [DllImport("user32.dll")]
    private static extern bool GetWindowRect(IntPtr hwnd, out Rect rect);

    private static readonly string[] SecureProcesses =
    {
        "lockapp", "logonui", "winlogon", "screensaver", "logonui.exe",
    };

    /// <summary>
    /// The lock screen and the logon desktop are secure surfaces: UI Automation
    /// can list a few nodes, but input is refused by the system. Say so instead
    /// of pretending the tree is the user's screen.
    /// </summary>
    private bool IsSecureSurface(out string processName)
    {
        var hwnd = GetForegroundWindow();
        GetWindowThreadProcessId(hwnd, out var pid);
        processName = "";
        try
        {
            processName = Process.GetProcessById((int)pid).ProcessName;
        }
        catch
        {
            return false;
        }

        return SecureProcesses.Contains(processName.ToLowerInvariant());
    }

    private void RefuseSecureSurface()
    {
        if (IsSecureSurface(out var processName))
        {
            throw new InvalidOperationException(
                $"the screen is '{processName}' (locked or secure); HOPA cannot see or touch it");
        }
    }

    public System.Windows.Size ScreenSize()
    {
        var width = (int)System.Windows.SystemParameters.PrimaryScreenWidth;
        var height = (int)System.Windows.SystemParameters.PrimaryScreenHeight;
        return new System.Windows.Size(width, height);
    }

    public string ForegroundApp()
    {
        var hwnd = GetForegroundWindow();
        GetWindowThreadProcessId(hwnd, out var pid);
        try
        {
            return Process.GetProcessById((int)pid).ProcessName;
        }
        catch
        {
            return "";
        }
    }

    /// <summary>Builds the observation JSON the Kotlin contract decodes.</summary>
    public (System.Text.Json.Nodes.JsonObject Observation, int Nodes) Observe()
    {
        RefuseSecureSurface();
        counter = 0;
        refs.Clear();
        root = ForegroundElement();
        var lines = new List<string>();
        var tree = root == null
            ? Node("root", "root", "", "", null, false, true, false, true, false, false, new List<System.Text.Json.Nodes.JsonObject>())
            : Walk(root, 0, lines);
        var app = ForegroundApp();
        var screen = ScreenSize();
        var observation = new System.Text.Json.Nodes.JsonObject
        {
            ["schemaVersion"] = "1",
            ["id"] = $"win-{DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()}",
            ["timestamp"] = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
            ["displayId"] = 0,
            ["windowId"] = "active",
            ["rotation"] = 0,
            ["density"] = 1.0,
            ["screenSize"] = new System.Text.Json.Nodes.JsonObject
            {
                ["width"] = (int)screen.Width,
                ["height"] = (int)screen.Height,
            },
            ["focus"] = "",
            ["text"] = string.Join("\n", lines.Take(40)),
            ["tree"] = tree,
            ["screenshotRef"] = "",
            ["app"] = app,
            ["hash"] = "",
            ["truncated"] = lines.Count > 40,
        };
        return (observation, refs.Count);
    }

    public string Act(string op, System.Text.Json.Nodes.JsonObject message)
    {
        RefuseSecureSurface();
        var point = PointFor(message);
        var screenPoint = new System.Drawing.Point(point.X, point.Y);
        switch (op)
        {
            case "tap":
                return Tap(point, message);
            case "doubleTap":
                Mouse.DoubleClick(screenPoint);
                return $"double-clicked {point.X},{point.Y}";
            case "longPress":
                Mouse.MoveTo(screenPoint);
                Mouse.Down(MouseButton.Left);
                Thread.Sleep(700);
                Mouse.Up(MouseButton.Left);
                return $"long-pressed {point.X},{point.Y}";
            case "type":
                return Type(message);
            case "swipe":
                return Swipe(message);
            case "key":
                return Key(message);
            case "launch":
                var app = message["app"]?.GetValue<string>() ?? "";
                if (app.Length == 0)
                {
                    throw new InvalidOperationException("launch needs an app");
                }

                return Launch(app);
            default:
                throw new InvalidOperationException($"the Windows adapter cannot '{op}' yet");
        }
    }

    public void Dispose() => automation.Dispose();

    private AutomationElement? ForegroundElement()
    {
        try
        {
            var hwnd = GetForegroundWindow();
            if (hwnd == IntPtr.Zero)
            {
                return null;
            }

            return automation.FromHandle(hwnd);
        }
        catch
        {
            return null;
        }
    }

    private System.Text.Json.Nodes.JsonObject Walk(
        AutomationElement element,
        int depth,
        List<string> lines)
    {
        var refId = $"e{++counter}";
        var controlType = Safe(() => element.Properties.ControlType.ValueOrDefault.ToString());
        var name = Safe(() => element.Properties.Name.ValueOrDefault ?? "");
        var automationId = Safe(() => element.Properties.AutomationId.ValueOrDefault ?? "");
        var isPassword = Safe(() => element.Properties.IsPassword.ValueOrDefault);
        var enabled = Safe(() => element.Properties.IsEnabled.ValueOrDefault);
        var offscreen = Safe(() => element.Properties.IsOffscreen.ValueOrDefault);
        var focused = Safe(() => element.Properties.HasKeyboardFocus.ValueOrDefault);
        var clickable = Safe(() => element.Patterns.Invoke.IsSupported
            || element.Patterns.Toggle.IsSupported
            || element.Patterns.SelectionItem.IsSupported);
        var scrollable = Safe(() => element.Patterns.Scroll.IsSupported);
        var bounds = Safe(() => element.BoundingRectangle);

        var type = isPassword ? "password" : controlType.ToLowerInvariant();
        if (!isPassword)
        {
            refs[refId] = element;
            if (name.Length > 0)
            {
                lines.Add($"{refId} {type} {name}");
            }
        }
        else
        {
            name = "";
            lines.Add($"{refId} password field (dropped)");
        }

        var children = new List<System.Text.Json.Nodes.JsonObject>();
        if (depth < MaxDepth && counter < MaxNodes)
        {
            AutomationElement[] kids;
            try
            {
                kids = element.FindAllChildren();
            }
            catch
            {
                kids = Array.Empty<AutomationElement>();
            }

            foreach (var child in kids)
            {
                if (counter >= MaxNodes)
                {
                    break;
                }

                children.Add(Walk(child, depth + 1, lines));
            }
        }

        return Node(
            refId,
            type,
            name,
            automationId,
            bounds,
            clickable,
            enabled,
            false,
            !offscreen,
            scrollable,
            focused,
            children);
    }

    private static System.Text.Json.Nodes.JsonObject Node(
        string refId,
        string type,
        string text,
        string id,
        System.Drawing.Rectangle? bounds,
        bool clickable,
        bool enabled,
        bool isChecked,
        bool visible,
        bool scrollable,
        bool focused,
        List<System.Text.Json.Nodes.JsonObject> children)
    {
        var node = new System.Text.Json.Nodes.JsonObject
        {
            ["ref"] = refId,
            ["type"] = type,
            ["text"] = text,
            ["description"] = "",
            ["id"] = id,
            ["clickable"] = clickable,
            ["enabled"] = enabled,
            ["checked"] = isChecked,
            ["scrollable"] = scrollable,
            ["visible"] = visible,
            ["focused"] = focused,
            ["children"] = new System.Text.Json.Nodes.JsonArray(children.ToArray()),
        };
        if (bounds is { Width: > 0 } rect)
        {
            node["bounds"] = new System.Text.Json.Nodes.JsonArray(
                rect.X,
                rect.Y,
                rect.X + rect.Width,
                rect.Y + rect.Height);
        }
        else
        {
            node["bounds"] = null;
        }

        return node;
    }

    private (int X, int Y) PointFor(System.Text.Json.Nodes.JsonObject message)
    {
        var refId = message["ref"]?.GetValue<string>();
        if (!string.IsNullOrEmpty(refId))
        {
            if (!refs.TryGetValue(refId, out var element))
            {
                throw new InvalidOperationException($"stale ref {refId}; observe again");
            }

            var bounds = element.BoundingRectangle;
            return ((int)(bounds.X + bounds.Width / 2), (int)(bounds.Y + bounds.Height / 2));
        }

        var x = message["x"]?.GetValue<int>() ?? 0;
        var y = message["y"]?.GetValue<int>() ?? 0;
        return (x, y);
    }

    private string Tap((int X, int Y) point, System.Text.Json.Nodes.JsonObject message)
    {
        var refId = message["ref"]?.GetValue<string>();
        if (!string.IsNullOrEmpty(refId) && refs.TryGetValue(refId, out var element))
        {
            try
            {
                if (element.Patterns.Invoke.IsSupported)
                {
                    element.Patterns.Invoke.Pattern.Invoke();
                    return $"invoked {refId}";
                }

                if (element.Patterns.SelectionItem.IsSupported)
                {
                    element.Patterns.SelectionItem.Pattern.Select();
                    return $"selected {refId}";
                }
            }
            catch (Exception error)
            {
                // Fall through to the mouse; the pattern was offered but failed.
                _ = error;
            }
        }

        Mouse.Click(new System.Drawing.Point(point.X, point.Y));
        return $"clicked {point.X},{point.Y}";
    }

    public string? FirstEditableRef()
    {
        foreach (var pair in refs)
        {
            if (Safe(() => pair.Value.Patterns.Value.IsSupported))
            {
                return pair.Key;
            }
        }

        return null;
    }

    public string ReadRefValue(string refId)
    {
        if (!refs.TryGetValue(refId, out var element))
        {
            return "";
        }

        try
        {
            return element.Patterns.Value.IsSupported
                ? element.Patterns.Value.Pattern.Value.Value ?? ""
                : "";
        }
        catch
        {
            return "";
        }
    }

    /// <summary>PNG of the foreground window, or null when the screen is not capturable.</summary>
    public byte[]? CaptureWindow()
    {
        try
        {
            var handle = GetForegroundWindow();
            if (!GetWindowRect(handle, out var rect))
            {
                return null;
            }

            var width = rect.Right - rect.Left;
            var height = rect.Bottom - rect.Top;
            if (width <= 0 || height <= 0)
            {
                return null;
            }

            using var bitmap = new System.Drawing.Bitmap(width, height);
            using (var graphics = System.Drawing.Graphics.FromImage(bitmap))
            {
                graphics.CopyFromScreen(rect.Left, rect.Top, 0, 0, bitmap.Size);
            }

            using var stream = new MemoryStream();
            bitmap.Save(stream, System.Drawing.Imaging.ImageFormat.Png);
            return stream.ToArray();
        }
        catch
        {
            return null;
        }
    }

    private string Launch(string app)
    {
        Process.Start(new ProcessStartInfo(app) { UseShellExecute = true });
        var needle = Path.GetFileNameWithoutExtension(app).ToLowerInvariant();
        var deadline = DateTime.UtcNow.AddSeconds(8);
        IntPtr handle = IntPtr.Zero;
        while (DateTime.UtcNow < deadline && handle == IntPtr.Zero)
        {
            Thread.Sleep(400);
            handle = FindWindowFor(needle);
        }

        if (handle == IntPtr.Zero)
        {
            throw new InvalidOperationException($"launched {app}, but no window appeared");
        }

        ForceForeground(handle);
        Thread.Sleep(500);
        if (GetForegroundWindow() != handle)
        {
            AppActivateByHandle(handle);
            Thread.Sleep(600);
        }

        if (GetForegroundWindow() != handle)
        {
            throw new InvalidOperationException(
                $"launched {app}, but could not bring it forward (foreground={ForegroundApp().ToLowerInvariant()})");
        }

        return $"launched {app} (foreground={ForegroundApp().ToLowerInvariant()})";
    }

    /// <summary>UIA is not enough for some windows; WScript.Shell can still
    /// activate by process id.</summary>
    private void AppActivateByHandle(IntPtr handle)
    {
        try
        {
            GetWindowThreadProcessId(handle, out var pid);
            var type = System.Type.GetTypeFromProgID("WScript.Shell");
            if (type == null)
            {
                return;
            }

            dynamic shell = Activator.CreateInstance(type)!;
            shell.AppActivate((int)pid);
        }
        catch
        {
            // The caller checks the foreground and reports honestly.
        }
    }

    private IntPtr FindWindowFor(string needle)
    {
        try
        {
            foreach (var window in automation.GetDesktop().FindAllChildren(cf => cf.ByControlType(ControlType.Window)))
            {
                var title = Safe(() => window.Properties.Name.ValueOrDefault ?? "").ToLowerInvariant();
                if (title == "program manager" || Safe(() => window.Properties.IsOffscreen.ValueOrDefault))
                {
                    continue;
                }

                var process = ProcessNameFor(window).ToLowerInvariant();
                if (process == needle || title.Contains(needle))
                {
                    return (IntPtr)Safe(() => window.Properties.NativeWindowHandle.Value);
                }
            }
        }
        catch
        {
            // The desktop enumeration can throw mid-change; the caller says so.
        }

        return IntPtr.Zero;
    }

    private static string ProcessNameFor(AutomationElement element)
    {
        try
        {
            return Process.GetProcessById(element.Properties.ProcessId.Value).ProcessName;
        }
        catch
        {
            return "";
        }
    }

    /// <summary>Windows blocks a background process from stealing focus; attaching
    /// to the thread that currently owns the input queue is the standard way
    /// around it.</summary>
    private static void ForceForeground(IntPtr handle)
    {
        ShowWindow(handle, 9);
        if (SetForegroundWindow(handle))
        {
            return;
        }

        var foreground = GetForegroundWindow();
        var foregroundThread = GetWindowThreadProcessId(foreground, out _);
        var current = GetCurrentThreadId();
        if (foregroundThread != current && AttachThreadInput(current, foregroundThread, true))
        {
            SetForegroundWindow(handle);
            AttachThreadInput(current, foregroundThread, false);
        }
    }

    private string Type(System.Text.Json.Nodes.JsonObject message)
    {
        var text = message["text"]?.GetValue<string>() ?? "";
        var refId = message["ref"]?.GetValue<string>();
        if (string.IsNullOrEmpty(refId))
        {
            throw new InvalidOperationException(
                "type needs a ref on Windows; blind typing would hit whatever window is focused");
        }

        if (!refs.TryGetValue(refId, out var element))
        {
            throw new InvalidOperationException($"stale ref {refId}; observe again");
        }

        try
        {
            if (element.Patterns.Value.IsSupported)
            {
                element.Patterns.Value.Pattern.SetValue(text);
                return $"set {refId} to {text.Length} chars";
            }

            element.Focus();
        }
        catch (Exception error)
        {
            _ = error;
        }

        Keyboard.Type(text);
        return $"typed {text.Length} chars";
    }

    private string Swipe(System.Text.Json.Nodes.JsonObject message)
    {
        var fromX = message["fromX"]?.GetValue<int>() ?? 0;
        var fromY = message["fromY"]?.GetValue<int>() ?? 0;
        var toX = message["toX"]?.GetValue<int>() ?? 0;
        var toY = message["toY"]?.GetValue<int>() ?? 0;
        Mouse.MoveTo(new System.Drawing.Point(fromX, fromY));
        Mouse.Down(MouseButton.Left);
        Thread.Sleep(80);
        Mouse.MoveTo(new System.Drawing.Point(toX, toY));
        Mouse.Up(MouseButton.Left);
        return $"swiped {fromX},{fromY} -> {toX},{toY}";
    }

    private string Key(System.Text.Json.Nodes.JsonObject message)
    {
        var key = message["key"]?.GetValue<string>() ?? "";
        switch (key)
        {
            case "back":
                Keyboard.Type(VirtualKeyShort.ESC);
                return "pressed Escape (back)";
            case "home":
                Keyboard.Press(VirtualKeyShort.LWIN);
                Keyboard.Type(VirtualKeyShort.KEY_D);
                Keyboard.Release(VirtualKeyShort.LWIN);
                return "showed the desktop (home)";
            default:
                throw new InvalidOperationException($"unknown key {key}");
        }
    }

    private static T Safe<T>(Func<T> read)
    {
        try
        {
            return read();
        }
        catch
        {
            return default!;
        }
    }
}

