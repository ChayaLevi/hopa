using System.Text.Json.Nodes;

namespace Hopa.Windows.Cards;

/// <summary>RFC 6901 pointers over an A2UI data model; both <c>""</c> and <c>"/"</c> mean the root.</summary>
internal static class JsonPointer
{
    public static IReadOnlyList<string> Tokens(string pointer)
    {
        if (pointer.Length == 0 || pointer == "/")
        {
            return [];
        }

        return pointer[1..].Split('/').Select(token => token.Replace("~1", "/").Replace("~0", "~")).ToList();
    }

    public static JsonNode? Get(JsonNode? root, string pointer)
    {
        var node = root;
        foreach (var token in Tokens(pointer))
        {
            node = node switch
            {
                JsonObject obj => obj[token],
                JsonArray array => int.TryParse(token, out var index) && index.ToString() == token &&
                                   index >= 0 && index < array.Count
                    ? array[index]
                    : null,
                _ => null,
            };
        }

        return node;
    }

    /// <summary>
    /// A copy of <paramref name="root"/> with <paramref name="value"/> at the
    /// pointer, creating objects on the way; a JSON null removes the key.
    /// Writing inside an array is not supported. Null when it can't be written.
    /// </summary>
    public static JsonObject? Set(JsonObject root, string pointer, JsonNode? value)
    {
        var tokens = Tokens(pointer);
        if (tokens.Count == 0)
        {
            return value switch
            {
                JsonObject obj => (JsonObject)obj.DeepClone(),
                null => new JsonObject(),
                _ => null,
            };
        }

        var copy = (JsonObject)root.DeepClone();
        var node = copy;
        for (var i = 0; i < tokens.Count - 1; i++)
        {
            if (!node.ContainsKey(tokens[i]))
            {
                node[tokens[i]] = new JsonObject();
            }

            if (node[tokens[i]] is not JsonObject next)
            {
                return null;
            }

            node = next;
        }

        if (value == null)
        {
            node.Remove(tokens[^1]);
        }
        else
        {
            node[tokens[^1]] = value.DeepClone();
        }

        return copy;
    }
}

/// <summary>What a component contains and what it shows, with JSON Pointer suffixes for error paths.</summary>
internal static class A2uiTree
{
    public static IReadOnlyList<(string Suffix, string Id)> ChildRefs(A2uiComponent component) => component switch
    {
        A2uiComponent.Card card => [("child", card.Child)],
        A2uiComponent.Button button => [("child", button.Child)],
        A2uiComponent.Row row => row.Children.Select((id, index) => ($"children/{index}", id)).ToList(),
        A2uiComponent.Column column => column.Children.Select((id, index) => ($"children/{index}", id)).ToList(),
        _ => [],
    };

    public static IEnumerable<string> ChildIds(A2uiComponent component) => ChildRefs(component).Select(r => r.Id);

    /// <summary>Every string property, with its JSON Pointer suffix, so bindings can be checked.</summary>
    public static IReadOnlyList<(string Suffix, A2uiText Value)> Texts(A2uiComponent component)
    {
        var own = new List<(string, A2uiText)>();
        switch (component)
        {
            case A2uiComponent.Text text:
                own.Add(("text", text.Value));
                break;
            case A2uiComponent.Button button:
                own.AddRange(button.Event.Context.Select(
                    pair => (A2uiFields.Pointer("action/event/context", pair.Key), pair.Value)));
                break;
            case A2uiComponent.PaymentNotice notice:
                own.Add(("amount", notice.Amount));
                if (notice.Merchant != null)
                {
                    own.Add(("merchant", notice.Merchant));
                }

                if (notice.Consequence != null)
                {
                    own.Add(("consequence", notice.Consequence));
                }

                own.Add(("acknowledge", notice.Acknowledge));
                break;
        }

        if (component.Accessibility?.Label is { } label)
        {
            own.Add(("@accessibility/label", label));
        }

        if (component.Accessibility?.Description is { } description)
        {
            own.Add(("@accessibility/description", description));
        }

        return own;
    }
}
