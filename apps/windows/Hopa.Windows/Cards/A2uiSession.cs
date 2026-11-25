using System.Text.Json;
using System.Text.Json.Nodes;

namespace Hopa.Windows.Cards;

/// <summary>What applying one message did.</summary>
internal abstract record A2uiOutcome
{
    /// <summary>The surface is complete and valid: draw it.</summary>
    public sealed record Ready(A2uiSurface Surface) : A2uiOutcome;

    /// <summary>Accepted, but there is no root yet: keep waiting.</summary>
    public sealed record Pending(string SurfaceId) : A2uiOutcome;

    public sealed record Deleted(string SurfaceId) : A2uiOutcome;

    /// <summary>Nothing changed.</summary>
    public sealed record Refused(string? SurfaceId, A2uiProblem Problem) : A2uiOutcome;
}

/// <summary>
/// A renderer's surfaces for its lifetime, a port of <c>A2uiSession.kt</c>.
/// Each message is applied whole or not at all, and a surface id is never
/// reused, even after a delete.
/// </summary>
internal sealed class A2uiSession
{
    private sealed record Draft(IReadOnlyDictionary<string, A2uiComponent> Components, JsonObject DataModel);

    private readonly Dictionary<string, Draft> live = new();
    private readonly HashSet<string> used = new();

    public A2uiOutcome Apply(JsonObject json)
    {
        A2uiMessage message;
        try
        {
            message = A2uiDecoder.Message(json);
        }
        catch (A2uiRefusal refusal)
        {
            return new A2uiOutcome.Refused(SurfaceIdOf(json), refusal.Problem);
        }

        try
        {
            return ApplyOrRefuse(message);
        }
        catch (A2uiRefusal refusal)
        {
            return new A2uiOutcome.Refused(message.SurfaceId, refusal.Problem);
        }
    }

    /// <summary>Applies the messages in order; stops at the first refusal.</summary>
    public A2uiOutcome ApplyAll(IEnumerable<JsonObject> messages)
    {
        A2uiOutcome outcome = new A2uiOutcome.Pending("");
        foreach (var message in messages)
        {
            outcome = Apply(message);
            if (outcome is A2uiOutcome.Refused)
            {
                return outcome;
            }
        }

        return outcome;
    }

    private A2uiOutcome ApplyOrRefuse(A2uiMessage message)
    {
        var id = message.SurfaceId;
        switch (message)
        {
            case A2uiMessage.CreateSurface create:
            {
                if (used.Contains(id))
                {
                    throw A2uiRefusal.Of(A2uiCode.IntegrityError, "/createSurface/surfaceId", $"{id} was already used");
                }

                var draft = new Draft(
                    create.Components.ToDictionary(c => c.Id),
                    create.DataModel ?? new JsonObject());
                var outcome = Commit(id, draft, "/createSurface/components", create.Components);
                used.Add(id);
                return outcome;
            }

            case A2uiMessage.UpdateComponents update:
            {
                var draft = Existing(id, "/updateComponents/surfaceId");
                var merged = new Dictionary<string, A2uiComponent>(draft.Components);
                foreach (var component in update.Components)
                {
                    merged[component.Id] = component;
                }

                if (merged.Count > A2ui.MaxComponents)
                {
                    throw A2uiRefusal.Of(A2uiCode.ValidationFailed, "/updateComponents/components", "too many components");
                }

                return Commit(id, new Draft(merged, draft.DataModel), "/updateComponents/components", update.Components);
            }

            case A2uiMessage.UpdateDataModel data:
            {
                var draft = Existing(id, "/updateDataModel/surfaceId");
                var model = JsonPointer.Set(draft.DataModel, data.Path, data.Value)
                    ?? throw A2uiRefusal.Of(A2uiCode.ValidationFailed, "/updateDataModel/path", $"{data.Path} cannot be written");
                return Commit(id, new Draft(draft.Components, model), "/updateDataModel", []);
            }

            default:
                Existing(id, "/deleteSurface/surfaceId");
                live.Remove(id);
                return new A2uiOutcome.Deleted(id);
        }
    }

    private Draft Existing(string id, string path) => live.TryGetValue(id, out var draft)
        ? draft
        : throw A2uiRefusal.Of(A2uiCode.IntegrityError, path, $"surface {id} does not exist");

    private A2uiOutcome Commit(string id, Draft draft, string basePath, IReadOnlyList<A2uiComponent> sent)
    {
        if (!draft.Components.ContainsKey(A2uiSurface.Root))
        {
            live[id] = draft;
            return new A2uiOutcome.Pending(id);
        }

        var index = new Dictionary<string, int>();
        for (var i = 0; i < sent.Count; i++)
        {
            index[sent[i].Id] = i;
        }

        new A2uiGraph(draft.Components, draft.DataModel, componentId =>
            index.TryGetValue(componentId, out var at) ? $"{basePath}/{at}" : basePath).Check();
        live[id] = draft;
        return new A2uiOutcome.Ready(new A2uiSurface(id, draft.Components, draft.DataModel));
    }

    private static string? SurfaceIdOf(JsonObject json)
    {
        foreach (var (_, value) in json)
        {
            if (value is JsonObject body && body.ContainsKey("surfaceId"))
            {
                return body["surfaceId"] is JsonValue id
                    ? id.GetValueKind() == JsonValueKind.String ? id.GetValue<string>() : id.ToJsonString()
                    : null;
            }
        }

        return null;
    }
}

/// <summary>
/// The rules JSON Schema can't say: one tree from root, every reference
/// resolves, no component twice, the catalog's allowed parents and children,
/// and every binding names a string in the data model.
/// </summary>
internal sealed class A2uiGraph(
    IReadOnlyDictionary<string, A2uiComponent> components,
    JsonObject dataModel,
    Func<string, string> where)
{
    public const string Surface = "Surface";

    /// <summary>Mirrors <c>allowedParents</c> in <c>spec/a2ui/hopa-catalog.v1.json</c>.</summary>
    private static readonly Dictionary<string, HashSet<string>> AllowedParents = new()
    {
        ["Card"] = [Surface],
        ["PaymentNotice"] = ["Column"],
    };

    /// <summary>Mirrors <c>allowedChildren</c> in <c>spec/a2ui/hopa-catalog.v1.json</c>.</summary>
    private static readonly Dictionary<string, HashSet<string>> AllowedChildren = new()
    {
        ["Button"] = ["Text", "Icon"],
    };

    private readonly HashSet<string> visited = new();

    public void Check()
    {
        Visit(components[A2uiSurface.Root], Surface, 1);
        var unreachable = components.Keys.FirstOrDefault(id => !visited.Contains(id));
        if (unreachable != null)
        {
            throw A2uiRefusal.Of(A2uiCode.ValidationFailed, where(unreachable), $"{unreachable} is not reachable from root");
        }
    }

    private void Visit(A2uiComponent component, string parentType, int depth)
    {
        visited.Add(component.Id);
        if (AllowedParents.TryGetValue(component.Type, out var parents) && !parents.Contains(parentType))
        {
            throw A2uiRefusal.Of(A2uiCode.UnallowedParent, where(component.Id), $"{component.Type} cannot sit in {parentType}");
        }

        if (depth > A2ui.MaxDepth)
        {
            throw A2uiRefusal.Of(A2uiCode.ValidationFailed, where(component.Id), "the tree is too deep");
        }

        foreach (var (suffix, value) in A2uiTree.Texts(component))
        {
            if (value is A2uiText.Bound bound &&
                !(JsonPointer.Get(dataModel, bound.Path) is JsonValue v && v.GetValueKind() == JsonValueKind.String))
            {
                throw A2uiRefusal.Of(A2uiCode.ValidationFailed, $"{where(component.Id)}/{suffix}", $"{bound.Path} is not a string");
            }
        }

        foreach (var (suffix, childId) in A2uiTree.ChildRefs(component))
        {
            var at = $"{where(component.Id)}/{suffix}";
            if (!components.TryGetValue(childId, out var child))
            {
                throw A2uiRefusal.Of(A2uiCode.ValidationFailed, at, $"{childId} does not exist");
            }

            if (AllowedChildren.TryGetValue(component.Type, out var allowed) && !allowed.Contains(child.Type))
            {
                throw A2uiRefusal.Of(A2uiCode.UnallowedChild, at, $"{component.Type} cannot hold {child.Type}");
            }

            if (visited.Contains(childId))
            {
                throw A2uiRefusal.Of(A2uiCode.ValidationFailed, at, $"{childId} is contained twice");
            }

            Visit(child, component.Type, depth + 1);
        }
    }
}

/// <summary>A surface that passed every rule. This is what a renderer draws.</summary>
internal sealed record A2uiSurface(
    string Id,
    IReadOnlyDictionary<string, A2uiComponent> Components,
    JsonObject DataModel)
{
    public const string Root = "root";

    public A2uiComponent RootComponent => Components[Root];

    public A2uiComponent? Component(string id) => Components.GetValueOrDefault(id);

    /// <summary>The string a value shows; bindings are checked before a surface exists.</summary>
    public string Text(A2uiText value) => value switch
    {
        A2uiText.Literal literal => literal.Value,
        A2uiText.Bound bound => JsonPointer.Get(DataModel, bound.Path) is JsonValue v &&
                                v.GetValueKind() == JsonValueKind.String
            ? v.GetValue<string>()
            : "",
        _ => "",
    };

    public IEnumerable<A2uiComponent> Children(A2uiComponent component) =>
        A2uiTree.ChildIds(component).Select(id => Components[id]);
}
