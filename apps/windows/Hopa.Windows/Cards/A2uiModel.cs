using System.Text.Json.Nodes;

namespace Hopa.Windows.Cards;

/// <summary>A string property: a literal, or a binding to a string in the data model.</summary>
internal abstract record A2uiText
{
    public sealed record Literal(string Value) : A2uiText;

    public sealed record Bound(string Path) : A2uiText;
}

internal sealed record A2uiAccessibility(A2uiText? Label, A2uiText? Description);

internal sealed record A2uiEvent(string Name, IReadOnlyList<KeyValuePair<string, A2uiText>> Context);

/// <summary>The HOPA catalog's components (<c>spec/a2ui/hopa-catalog.v1.json</c>).</summary>
internal abstract record A2uiComponent(string Id, A2uiAccessibility? Accessibility)
{
    public abstract string Type { get; }

    public sealed record Text(string Id, A2uiText Value, string? Variant, A2uiAccessibility? Accessibility)
        : A2uiComponent(Id, Accessibility)
    {
        public override string Type => "Text";
    }

    public sealed record Row(
        string Id,
        IReadOnlyList<string> Children,
        string? Justify,
        string? Align,
        A2uiAccessibility? Accessibility) : A2uiComponent(Id, Accessibility)
    {
        public override string Type => "Row";
    }

    public sealed record Column(
        string Id,
        IReadOnlyList<string> Children,
        string? Justify,
        string? Align,
        A2uiAccessibility? Accessibility) : A2uiComponent(Id, Accessibility)
    {
        public override string Type => "Column";
    }

    public sealed record Card(string Id, string Child, A2uiAccessibility? Accessibility)
        : A2uiComponent(Id, Accessibility)
    {
        public override string Type => "Card";
    }

    public sealed record Button(
        string Id,
        string Child,
        A2uiEvent Event,
        string? Variant,
        A2uiAccessibility? Accessibility) : A2uiComponent(Id, Accessibility)
    {
        public override string Type => "Button";
    }

    public sealed record Divider(string Id, string? Axis, A2uiAccessibility? Accessibility)
        : A2uiComponent(Id, Accessibility)
    {
        public override string Type => "Divider";
    }

    public sealed record Icon(string Id, string Name, A2uiAccessibility? Accessibility)
        : A2uiComponent(Id, Accessibility)
    {
        public override string Type => "Icon";
    }

    public sealed record PaymentNotice(
        string Id,
        A2uiText Amount,
        A2uiText Acknowledge,
        A2uiText? Merchant,
        A2uiText? Consequence,
        A2uiAccessibility? Accessibility) : A2uiComponent(Id, Accessibility)
    {
        public override string Type => "PaymentNotice";
    }
}

/// <summary>Agent-to-renderer messages, after a strict decode.</summary>
internal abstract record A2uiMessage(string SurfaceId)
{
    public sealed record CreateSurface(
        string SurfaceId,
        IReadOnlyList<A2uiComponent> Components,
        JsonObject? DataModel) : A2uiMessage(SurfaceId);

    public sealed record UpdateComponents(string SurfaceId, IReadOnlyList<A2uiComponent> Components)
        : A2uiMessage(SurfaceId);

    /// <summary><see cref="Value"/> is null for a JSON null, which removes the value.</summary>
    public sealed record UpdateDataModel(string SurfaceId, JsonNode? Value, string Path) : A2uiMessage(SurfaceId);

    public sealed record DeleteSurface(string SurfaceId) : A2uiMessage(SurfaceId);
}
