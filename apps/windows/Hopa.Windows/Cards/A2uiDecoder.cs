using System.Text.Encodings.Web;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace Hopa.Windows.Cards;

/// <summary>
/// Agent-to-renderer messages, strictly: the HOPA subset of A2UI 1.0 or a
/// refusal. A port of <c>A2uiDecoder.kt</c>; the checks run in the same order,
/// so both report the same first problem for the same message.
/// </summary>
internal static class A2uiDecoder
{
    private static readonly HashSet<string> Payloads = ["createSurface", "updateComponents", "updateDataModel", "deleteSurface"];
    private static readonly HashSet<string> RefusedPayloads = ["callRendererFunction", "agentFunctionResponse"];
    private static readonly HashSet<string> Common = ["id", "component", "@accessibility"];
    private static readonly HashSet<string> RefusedCommon = ["@catalogId", "@metadata", "@checks", "weight"];
    private static readonly HashSet<string> Align = ["start", "center", "end", "stretch"];
    private static readonly HashSet<string> Justify = ["start", "center", "end", "spaceBetween"];
    private static readonly HashSet<string> TextVariants = ["caption", "body"];
    private static readonly HashSet<string> ButtonVariants = ["default", "primary", "borderless"];
    private static readonly HashSet<string> Axes = ["horizontal", "vertical"];

    public static readonly HashSet<string> Icons =
        ["check", "close", "error", "help", "info", "lock", "payment", "send", "shoppingCart", "warning"];

    private static readonly Dictionary<string, HashSet<string>> Props = new()
    {
        ["Text"] = ["text", "variant"],
        ["Row"] = ["children", "justify", "align"],
        ["Column"] = ["children", "justify", "align"],
        ["Card"] = ["child"],
        ["Button"] = ["child", "variant", "action"],
        ["Divider"] = ["axis"],
        ["Icon"] = ["name"],
        ["PaymentNotice"] = ["amount", "merchant", "consequence", "acknowledge"],
    };

    // Kotlin's compact JSON leaves non-ASCII as is; measure the same way.
    private static readonly JsonSerializerOptions Compact = new() { Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping };

    public static A2uiMessage Message(JsonObject json)
    {
        if (json.ToJsonString(Compact).Length > A2ui.MaxMessageChars)
        {
            throw A2uiRefusal.Of(A2uiCode.ValidationFailed, "", "the message is too large");
        }

        Version(json);
        var payloads = json.Select(pair => pair.Key).Where(key => key != "version").ToList();
        if (payloads.Count != 1)
        {
            throw A2uiRefusal.Of(A2uiCode.ValidationFailed, "", "a message carries exactly one payload");
        }

        var key = payloads[0];
        if (RefusedPayloads.Contains(key))
        {
            throw A2uiRefusal.Of(A2uiCode.Unsupported, $"/{key}", $"{key} is not supported by HOPA");
        }

        if (!Payloads.Contains(key))
        {
            throw A2uiRefusal.Of(A2uiCode.ValidationFailed, $"/{key}", $"unknown message {key}");
        }

        var body = A2uiFields.ObjectAt(json[key], $"/{key}");
        switch (key)
        {
            case "createSurface":
                return CreateSurface(body);
            case "updateComponents":
            {
                var fields = new A2uiFields(body, "/updateComponents", new HashSet<string> { "surfaceId", "components" });
                var surfaceId = fields.SurfaceId();
                return new A2uiMessage.UpdateComponents(
                    surfaceId,
                    Components(fields.Required("components"), fields.At("components")));
            }

            case "updateDataModel":
            {
                var fields = new A2uiFields(body, "/updateDataModel", new HashSet<string> { "surfaceId", "path", "value" });
                var surfaceId = fields.SurfaceId();
                var path = fields.OptString("path") ?? "/";
                if (!path.StartsWith('/') || path.Length > A2ui.MaxPath)
                {
                    throw A2uiRefusal.Of(A2uiCode.ValidationFailed, fields.At("path"), "path must be a JSON Pointer");
                }

                return new A2uiMessage.UpdateDataModel(surfaceId, fields.Required("value")?.DeepClone(), path);
            }

            default:
                return new A2uiMessage.DeleteSurface(
                    new A2uiFields(body, "/deleteSurface", new HashSet<string> { "surfaceId" }).SurfaceId());
        }
    }

    public static void Version(JsonObject json)
    {
        if (!json.ContainsKey("version"))
        {
            throw A2uiRefusal.Of(A2uiCode.ValidationFailed, "/version", "version is required");
        }

        var value = json["version"] is JsonValue v && v.GetValueKind() == JsonValueKind.String ? v.GetValue<string>() : null;
        if (value != A2ui.Version)
        {
            throw A2uiRefusal.Of(A2uiCode.Unsupported, "/version", $"only A2UI {A2ui.Version} is supported");
        }
    }

    private static A2uiMessage.CreateSurface CreateSurface(JsonObject body)
    {
        var fields = new A2uiFields(
            body,
            "/createSurface",
            new HashSet<string> { "surfaceId", "catalogId", "sendDataModel", "components", "dataModel" },
            new HashSet<string> { "metadata" });
        var surfaceId = fields.SurfaceId();
        if (fields.String("catalogId") != A2ui.CatalogId)
        {
            throw A2uiRefusal.Of(A2uiCode.Unsupported, fields.At("catalogId"), "only the HOPA catalog is supported");
        }

        if (fields.Bool("sendDataModel") == true)
        {
            throw A2uiRefusal.Of(A2uiCode.Unsupported, fields.At("sendDataModel"), "the data model is never sent back");
        }

        var components = fields.Has("components")
            ? Components(fields.Element("components"), fields.At("components"))
            : [];
        var dataModel = fields.Has("dataModel")
            ? (JsonObject)A2uiFields.ObjectAt(fields.Element("dataModel"), fields.At("dataModel")).DeepClone()
            : null;
        return new A2uiMessage.CreateSurface(surfaceId, components, dataModel);
    }

    private static List<A2uiComponent> Components(JsonNode? element, string path)
    {
        var array = element as JsonArray
            ?? throw A2uiRefusal.Of(A2uiCode.ValidationFailed, path, "components must be an array");
        if (array.Count == 0 || array.Count > A2ui.MaxComponents)
        {
            throw A2uiRefusal.Of(A2uiCode.ValidationFailed, path, $"1 to {A2ui.MaxComponents} components");
        }

        var seen = new HashSet<string>();
        var result = new List<A2uiComponent>();
        for (var index = 0; index < array.Count; index++)
        {
            var component = Component(A2uiFields.ObjectAt(array[index], $"{path}/{index}"), $"{path}/{index}");
            if (!seen.Add(component.Id))
            {
                throw A2uiRefusal.Of(A2uiCode.ValidationFailed, $"{path}/{index}/id", $"{component.Id} appears twice");
            }

            result.Add(component);
        }

        return result;
    }

    private static A2uiComponent Component(JsonObject obj, string path)
    {
        var type = obj.ContainsKey("component")
            ? A2uiFields.StringAt(obj["component"], $"{path}/component")
            : throw A2uiRefusal.Of(A2uiCode.ValidationFailed, $"{path}/component", "component is required");
        if (!Props.TryGetValue(type, out var props))
        {
            throw A2uiRefusal.Of(A2uiCode.ValidationFailed, $"{path}/component", $"{type} is not in the HOPA catalog");
        }

        var f = new A2uiFields(obj, path, Common.Concat(props).ToHashSet(), RefusedCommon);
        var id = f.Id("id");
        var a11y = f.Has("@accessibility") ? Accessibility(f.Element("@accessibility"), f.At("@accessibility")) : null;
        switch (type)
        {
            case "Text":
            {
                var text = Text(f.Required("text"), f.At("text"));
                return new A2uiComponent.Text(id, text, f.Enum("variant", TextVariants), a11y);
            }

            case "Row":
            {
                var children = Children(f);
                var justify = f.Enum("justify", Justify);
                return new A2uiComponent.Row(id, children, justify, f.Enum("align", Align), a11y);
            }

            case "Column":
            {
                var children = Children(f);
                var justify = f.Enum("justify", Justify);
                return new A2uiComponent.Column(id, children, justify, f.Enum("align", Align), a11y);
            }

            case "Card":
                return new A2uiComponent.Card(id, f.Id("child"), a11y);
            case "Button":
            {
                var child = f.Id("child");
                var ev = Event(f.Required("action"), f.At("action"));
                return new A2uiComponent.Button(id, child, ev, f.Enum("variant", ButtonVariants), a11y);
            }

            case "Divider":
                return new A2uiComponent.Divider(id, f.Enum("axis", Axes), a11y);
            case "Icon":
            {
                var name = f.String("name");
                return Icons.Contains(name)
                    ? new A2uiComponent.Icon(id, name, a11y)
                    : throw A2uiRefusal.Of(A2uiCode.ValidationFailed, f.At("name"), $"unknown icon {name}");
            }

            default:
            {
                var amount = Text(f.Required("amount"), f.At("amount"));
                var acknowledge = Text(f.Required("acknowledge"), f.At("acknowledge"));
                var merchant = f.Has("merchant") ? Text(f.Element("merchant"), f.At("merchant")) : null;
                var consequence = f.Has("consequence") ? Text(f.Element("consequence"), f.At("consequence")) : null;
                return new A2uiComponent.PaymentNotice(id, amount, acknowledge, merchant, consequence, a11y);
            }
        }
    }

    private static List<string> Children(A2uiFields f)
    {
        var element = f.Required("children");
        if (element is JsonObject)
        {
            throw A2uiRefusal.Of(A2uiCode.Unsupported, f.At("children"), "children templates are not supported");
        }

        var array = f.Array("children");
        if (array.Count == 0 || array.Count > A2ui.MaxChildren)
        {
            throw A2uiRefusal.Of(A2uiCode.ValidationFailed, f.At("children"), $"1 to {A2ui.MaxChildren} children");
        }

        var ids = new List<string>();
        for (var index = 0; index < array.Count; index++)
        {
            var at = $"{f.At("children")}/{index}";
            var child = A2uiFields.StringAt(array[index], at);
            if (!A2ui.Id.IsMatch(child))
            {
                throw A2uiRefusal.Of(A2uiCode.ValidationFailed, at, "bad child id");
            }

            ids.Add(child);
        }

        return ids;
    }

    private static A2uiEvent Event(JsonNode? element, string path)
    {
        var action = new A2uiFields(
            A2uiFields.ObjectAt(element, path),
            path,
            new HashSet<string> { "event" },
            new HashSet<string> { "functionCall" });
        var ev = new A2uiFields(
            action.Obj("event"),
            action.At("event"),
            new HashSet<string> { "name", "context" },
            new HashSet<string> { "userMessage" });
        var name = ev.String("name");
        if (!A2ui.EventName.IsMatch(name))
        {
            throw A2uiRefusal.Of(A2uiCode.ValidationFailed, ev.At("name"), "bad event name");
        }

        var context = ev.Has("context") ? A2uiFields.ObjectAt(ev.Element("context"), ev.At("context")) : new JsonObject();
        if (context.Count > A2ui.MaxContext)
        {
            throw A2uiRefusal.Of(A2uiCode.ValidationFailed, ev.At("context"), "too many context keys");
        }

        var values = new List<KeyValuePair<string, A2uiText>>();
        foreach (var (key, value) in context)
        {
            var at = A2uiFields.Pointer(ev.At("context"), key);
            if (!A2ui.ContextKey.IsMatch(key))
            {
                throw A2uiRefusal.Of(A2uiCode.ValidationFailed, at, "bad context key");
            }

            values.Add(new(key, Text(value, at)));
        }

        return new A2uiEvent(name, values);
    }

    private static A2uiAccessibility Accessibility(JsonNode? element, string path)
    {
        var f = new A2uiFields(A2uiFields.ObjectAt(element, path), path, new HashSet<string> { "label", "description" });
        var label = f.Has("label") ? Text(f.Element("label"), f.At("label")) : null;
        var description = f.Has("description") ? Text(f.Element("description"), f.At("description")) : null;
        return new A2uiAccessibility(label, description);
    }

    public static A2uiText Text(JsonNode? element, string path)
    {
        if (element is JsonObject obj)
        {
            if (obj.ContainsKey("@call"))
            {
                throw A2uiRefusal.Of(A2uiCode.Unsupported, A2uiFields.Pointer(path, "@call"), "function calls are not supported");
            }

            var f = new A2uiFields(obj, path, new HashSet<string> { "@path" });
            var bound = f.String("@path");
            if (!bound.StartsWith('/') || bound.Length > A2ui.MaxPath)
            {
                throw A2uiRefusal.Of(A2uiCode.ValidationFailed, f.At("@path"), "@path must be a JSON Pointer");
            }

            return new A2uiText.Bound(bound);
        }

        var literal = A2uiFields.StringAt(element, path);
        return literal.Length > A2ui.MaxText
            ? throw A2uiRefusal.Of(A2uiCode.ValidationFailed, path, "text is too long")
            : new A2uiText.Literal(literal);
    }
}
