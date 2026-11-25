using System.Text.RegularExpressions;

namespace Hopa.Windows.Cards;

/// <summary>
/// HOPA's pinned A2UI contract (ADR-0031), the same values as
/// <c>core/contracts/.../a2ui/A2ui.kt</c>. The shared fixtures in
/// <c>spec/fixtures/a2ui/</c> keep the two in step.
/// </summary>
internal static class A2ui
{
    public const string Version = "1.0";
    public const string CatalogId = "https://hopa.dev/contracts/a2ui/hopa-catalog.v1.json";

    public const int MaxComponents = 64;
    public const int MaxChildren = 16;
    public const int MaxDepth = 8;
    public const int MaxText = 1000;
    public const int MaxPath = 256;
    public const int MaxContext = 8;
    public const int MaxMessageChars = 32_768;

    // \z, not $: .NET's $ also matches before a trailing newline.
    public static readonly Regex Id = new(@"^[A-Za-z_][A-Za-z0-9_]{0,63}\z", RegexOptions.CultureInvariant);
    public static readonly Regex SurfaceId = new(@"^[A-Za-z0-9_.:-]{1,128}\z", RegexOptions.CultureInvariant);
    public static readonly Regex EventName = new(@"^[a-z][A-Za-z]{0,31}\z", RegexOptions.CultureInvariant);
    public static readonly Regex ContextKey = new(@"^[A-Za-z_][A-Za-z0-9_]{0,31}\z", RegexOptions.CultureInvariant);
}

/// <summary>The A2UI 1.0 error codes HOPA reports.</summary>
internal static class A2uiCode
{
    public const string ValidationFailed = "VALIDATION_FAILED";
    public const string UnallowedParent = "UNALLOWED_PARENT";
    public const string UnallowedChild = "UNALLOWED_CHILD";
    public const string IntegrityError = "INTEGRITY_ERROR";
    public const string Unsupported = "UNSUPPORTED";
}

/// <summary>Why a message was refused; <see cref="Path"/> is a JSON Pointer into that message.</summary>
internal sealed record A2uiProblem(string Code, string Path, string Message);

/// <summary>Thrown inside the decoder and the session; never escapes them.</summary>
internal sealed class A2uiRefusal : Exception
{
    private A2uiRefusal(A2uiProblem problem)
        : base(problem.Message) => Problem = problem;

    public A2uiProblem Problem { get; }

    public static A2uiRefusal Of(string code, string path, string message) => new(new A2uiProblem(code, path, message));
}
