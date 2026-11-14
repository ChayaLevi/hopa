using System.ComponentModel;

namespace Hopa.Windows;

/// <summary>One row in the sidebar.</summary>
public sealed class ConversationVm : INotifyPropertyChanged
{
    private string title = "";
    private string updatedLabel = "";
    private bool pinned;
    private bool archived;

    public event PropertyChangedEventHandler? PropertyChanged;

    public string Id { get; init; } = "";

    public string SearchText { get; init; } = "";

    public int MessageCount { get; set; }

    public long UpdatedAt { get; set; }

    public string Title
    {
        get => title;
        set
        {
            if (title == value)
            {
                return;
            }

            title = value;
            Raise(nameof(Title));
        }
    }

    public string UpdatedLabel
    {
        get => updatedLabel;
        set
        {
            if (updatedLabel == value)
            {
                return;
            }

            updatedLabel = value;
            Raise(nameof(UpdatedLabel));
        }
    }

    public bool Pinned
    {
        get => pinned;
        set
        {
            if (pinned == value)
            {
                return;
            }

            pinned = value;
            Raise(nameof(Pinned));
            Raise(nameof(PinLabel));
        }
    }

    public bool Archived
    {
        get => archived;
        set
        {
            if (archived == value)
            {
                return;
            }

            archived = value;
            Raise(nameof(Archived));
            Raise(nameof(ArchivedLabel));
        }
    }

    public System.Windows.Media.Brush TitleInk => Selected
        ? new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(0x1C, 0x1B, 0x18))
        : new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(0x6B, 0x66, 0x5C));

    private bool selected;

    public bool Selected
    {
        get => selected;
        set
        {
            if (selected == value)
            {
                return;
            }

            selected = value;
            Raise(nameof(Selected));
            Raise(nameof(TitleInk));
        }
    }

    public string PinLabel => pinned ? "PIN" : "";

    public string ArchivedLabel => archived ? "archived" : "";

    private void Raise(string name) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
}
