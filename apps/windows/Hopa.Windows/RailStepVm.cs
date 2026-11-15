using System.Windows.Media;

namespace Hopa.Windows;

/// <summary>One step in the right rail: what was tried, what happened, and its colour.</summary>
public sealed class RailStepVm
{
    public string Number { get; init; } = "";

    public string Verb { get; init; } = "";

    public string Detail { get; init; } = "";

    public Brush Dot { get; init; } = Brushes.Gray;
}
