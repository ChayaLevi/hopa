using System.Text.Json;
using System.Text.Json.Nodes;

namespace Hopa.Windows.Cards;

/// <summary>
/// Strict reading of one JSON object at a path. Every key must be allowed; a
/// refused key is a real A2UI feature HOPA does not take (UNSUPPORTED),
/// anything else is unknown (VALIDATION_FAILED). Keys are checked first, so a
/// refused feature is named even when a required field is also missing.
/// System.Text.Json reads a JSON null as a null node, so presence is always
/// asked with <see cref="Has"/>, never by comparing a value with null.
/// </summary>
internal sealed class A2uiFields
{
    private readonly JsonObject obj;
    private readonly string path;

    public A2uiFields(JsonObject obj, string path, IReadOnlySet<string> allowed, IReadOnlySet<string>? refused = null)
    {
        this.obj = obj;
        this.path = path;
        foreach (var (key, _) in obj)
        {
            if (allowed.Contains(key))
            {
                continue;
            }

            if (refused != null && refused.Contains(key))
            {
                throw A2uiRefusal.Of(A2uiCode.Unsupported, At(key), $"{key} is not supported by HOPA");
            }

            throw A2uiRefusal.Of(A2uiCode.ValidationFailed, At(key), $"unknown property {key}");
        }
    }

    public string At(string key) => Pointer(path, key);

    public bool Has(string key) => obj.ContainsKey(key);

    public JsonNode? Element(string key) => obj[key];

    public JsonNode? Required(string key) => Has(key)
        ? obj[key]
        : throw A2uiRefusal.Of(A2uiCode.ValidationFailed, At(key), $"{key} is required");

    public string String(string key) => StringAt(Required(key), At(key));

    public string? OptString(string key) => Has(key) ? StringAt(obj[key], At(key)) : null;

    public string? Enum(string key, IReadOnlySet<string> values)
    {
        var value = OptString(key);
        if (value != null && !values.Contains(value))
        {
            throw A2uiRefusal.Of(A2uiCode.ValidationFailed, At(key), $"{key} must be one of {string.Join(", ", values)}");
        }

        return value;
    }

    public string Id(string key)
    {
        var value = String(key);
        return A2ui.Id.IsMatch(value)
            ? value
            : throw A2uiRefusal.Of(A2uiCode.ValidationFailed, At(key), $"{value} is not a HOPA component id");
    }

    public string SurfaceId()
    {
        var value = String("surfaceId");
        return A2ui.SurfaceId.IsMatch(value)
            ? value
            : throw A2uiRefusal.Of(A2uiCode.ValidationFailed, At("surfaceId"), "bad surface id");
    }

    public JsonObject Obj(string key) => ObjectAt(Required(key), At(key));

    public JsonArray Array(string key) => Required(key) as JsonArray
        ?? throw A2uiRefusal.Of(A2uiCode.ValidationFailed, At(key), $"{key} must be an array");

    public bool? Bool(string key)
    {
        if (!Has(key))
        {
            return null;
        }

        return obj[key] is JsonValue value && value.GetValueKind() is JsonValueKind.True or JsonValueKind.False
            ? value.GetValue<bool>()
            : throw A2uiRefusal.Of(A2uiCode.ValidationFailed, At(key), $"{key} must be a boolean");
    }

    public static string Pointer(string basePath, string key) =>
        $"{basePath}/" + key.Replace("~", "~0").Replace("/", "~1");

    public static string StringAt(JsonNode? node, string path) =>
        node is JsonValue value && value.GetValueKind() == JsonValueKind.String
            ? value.GetValue<string>()
            : throw A2uiRefusal.Of(A2uiCode.ValidationFailed, path, "must be a string");

    public static JsonObject ObjectAt(JsonNode? node, string path) =>
        node as JsonObject ?? throw A2uiRefusal.Of(A2uiCode.ValidationFailed, path, "must be an object");
}
