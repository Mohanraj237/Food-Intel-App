import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { UseQueryResult } from '@tanstack/react-query';
import type { HouseholdOverview } from '@/lib/dashboard';
import { overview } from '@/test/familyOverviewFixture';
import { FamilyWellness } from './FamilyWellness';

const useFamilyOverview = vi.fn();

vi.mock('@/lib/dashboard', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/lib/dashboard')>()),
  useFamilyOverview: (...args: unknown[]) => useFamilyOverview(...args),
}));

vi.mock('@/design-system/LogSheet', () => ({
  useLogSheet: () => ({ open: vi.fn() }),
}));

// Pin "today" in the household zone to the fixture's day, whatever the machine clock says.
vi.mock('@/features/goals/calendarDay', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/features/goals/calendarDay')>()),
  todayInZone: () => '2026-09-13',
}));

function loaded(data: HouseholdOverview): Partial<UseQueryResult<HouseholdOverview>> {
  return { isPending: false, isError: false, isPlaceholderData: false, data, refetch: vi.fn() };
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/family']}>
      <FamilyWellness householdId="h-iyer" />
    </MemoryRouter>,
  );
}

describe('FamilyWellness', () => {
  beforeEach(() => {
    useFamilyOverview.mockReset();
  });

  it('shows the family summary, one card per person who shares, and the updates feed', () => {
    useFamilyOverview.mockReturnValue(loaded(overview()));
    renderPage();

    const summary = screen.getByRole('region', { name: 'Family today' });
    expect(within(summary).getByText('From you, Aarav, Krishnan and Meera: the records you can see.')).toBeInTheDocument();
    expect(within(summary).getByText('4.7 L')).toBeInTheDocument();

    // Cards for everyone who shares something — never for someone who doesn't.
    const cards = screen.getAllByRole('article');
    expect(cards.map((c) => c.getAttribute('aria-label'))).toEqual(['Raghav', 'Aarav', 'Krishnan', 'Meera']);
    expect(within(cards[2]!).getByText('132/84 mmHg')).toBeInTheDocument();
    expect(within(cards[1]!).getByText('2 meals')).toBeInTheDocument();

    expect(screen.getByRole('region', { name: 'Latest updates' })).toBeInTheDocument();
  });

  it('lists people who share nothing quietly, a child with the guardian line', () => {
    useFamilyOverview.mockReturnValue(loaded(overview()));
    renderPage();

    const others = screen.getByRole('region', { name: 'Also in Iyer family' });
    expect(within(others).getByText('Arjun')).toBeInTheDocument();
    expect(within(others).getByText('Not shared with you')).toBeInTheDocument();
    expect(within(others).getByText('Their guardians look after their records.')).toBeInTheDocument();
    expect(within(others).queryByRole('link')).toBeNull();
  });

  it('never shows a summary built from fewer than two people', () => {
    useFamilyOverview.mockReturnValue(loaded(overview({ summary: null })));
    renderPage();
    expect(screen.queryByRole('region', { name: 'Family today' })).toBeNull();
  });

  it('shows the waiting strip only when something is waiting', () => {
    useFamilyOverview.mockReturnValue(loaded(overview({ mealsAwaitingConfirmation: 2 })));
    renderPage();
    expect(screen.getByText("Your share of 2 meals isn't in your log yet.")).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Review/ })).toHaveAttribute('href', '/meals?filter=awaiting');
  });

  it('offers a household of one the quiet "just you" card and nothing else', () => {
    useFamilyOverview.mockReturnValue(loaded(overview({ individual: true, members: [], summary: null })));
    renderPage();
    expect(screen.getByText("It's just you for now")).toBeInTheDocument();
    expect(screen.queryByRole('group', { name: 'Choose a day' })).toBeNull();
  });

  it('shows one error state, with retry, when the overview fails', () => {
    useFamilyOverview.mockReturnValue({ isPending: false, isError: true, error: new Error('boom'), refetch: vi.fn() });
    renderPage();
    expect(screen.getByRole('button', { name: /try again|retry/i })).toBeInTheDocument();
  });

  it('disables "next day" on today', () => {
    useFamilyOverview.mockReturnValue(loaded(overview()));
    renderPage();
    expect(screen.getByRole('button', { name: 'Next day' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Previous day' })).toBeEnabled();
  });
});
