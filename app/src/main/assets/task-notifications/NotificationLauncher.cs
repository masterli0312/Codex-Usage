using System;
using System.Diagnostics;
using System.Text;

// Compiled as a Windows GUI executable so Task Scheduler and Codex never create a console.
internal static class NotificationLauncher
{
    private static int Main(string[] args)
    {
        if (args.Length < 2) return 2;
        try
        {
            var command = new StringBuilder();
            for (int i = 1; i < args.Length; i++)
            {
                if (i > 1) command.Append(' ');
                command.Append(QuoteArgument(args[i]));
            }
            var start = new ProcessStartInfo(args[0], command.ToString())
            {
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true
            };
            using (var child = Process.Start(start))
            {
                child.OutputDataReceived += delegate { };
                child.ErrorDataReceived += delegate { };
                child.BeginOutputReadLine();
                child.BeginErrorReadLine();
                child.WaitForExit();
                return child.ExitCode;
            }
        }
        catch
        {
            // Arguments can contain a Codex event. Never print or persist them on failure.
            return 1;
        }
    }

    // Preserve empty arguments, quotes and trailing backslashes in Windows argv decoding.
    private static string QuoteArgument(string value)
    {
        var result = new StringBuilder("\"");
        int slashes = 0;
        foreach (char c in value)
        {
            if (c == '\\') { slashes++; continue; }
            if (c == '"') result.Append('\\', slashes * 2 + 1);
            else result.Append('\\', slashes);
            result.Append(c);
            slashes = 0;
        }
        result.Append('\\', slashes * 2);
        return result.Append('"').ToString();
    }
}
