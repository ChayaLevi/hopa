namespace Hopa.Windows.Cards;

/// <summary>
/// How an approval surface maps onto the approval window, as on Android: the
/// first Text is the title, the last Row holds the window's two buttons, and
/// everything between is the body. Null when the surface doesn't have that
/// shape, so the window never guesses which button means yes.
/// </summary>
internal sealed record ApprovalLayout(
    A2uiComponent.Text Title,
    IReadOnlyList<A2uiComponent> Body,
    A2uiComponent.PaymentNotice? Notice,
    A2uiComponent.Button Approve,
    A2uiComponent.Button Deny)
{
    public static ApprovalLayout? Of(A2uiSurface surface)
    {
        if (surface.RootComponent is not A2uiComponent.Card card ||
            surface.Component(card.Child) is not A2uiComponent.Column content)
        {
            return null;
        }

        var children = surface.Children(content).ToList();
        if (children.Count < 2 ||
            children[0] is not A2uiComponent.Text title ||
            children[^1] is not A2uiComponent.Row row)
        {
            return null;
        }

        var inRow = surface.Children(row).ToList();
        var buttons = inRow.OfType<A2uiComponent.Button>().ToList();
        var approve = buttons.Where(b => b.Event.Name == ApprovalCards.Approve).ToList();
        var deny = buttons.Where(b => b.Event.Name == ApprovalCards.Deny).ToList();
        if (inRow.Count != 2 || buttons.Count != 2 || approve.Count != 1 || deny.Count != 1)
        {
            return null;
        }

        var body = children.Skip(1).Take(children.Count - 2).ToList();
        var notices = body.OfType<A2uiComponent.PaymentNotice>().ToList();
        return new ApprovalLayout(title, body, notices.Count == 1 ? notices[0] : null, approve[0], deny[0]);
    }
}
