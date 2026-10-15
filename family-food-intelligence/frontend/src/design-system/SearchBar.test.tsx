import { describe, expect, it, vi, beforeEach } from 'vitest';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { useState } from 'react';
import type { Suggestion } from '@/lib/catalog';
import { SearchBar } from './SearchBar';

const getSuggestions = vi.fn<(q: string) => Promise<Suggestion[]>>();
vi.mock('@/lib/catalog', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/lib/catalog')>()),
  getSuggestions: (q: string) => getSuggestions(q),
}));

const SUGGESTIONS: Suggestion[] = [
  { kind: 'BRAND', label: 'Amul', target: 'Amul', secondary: '45' },
  { kind: 'CATEGORY', label: 'Dairy', target: 'DAIRY', secondary: null },
  { kind: 'PRODUCT', label: 'Amul Butter', target: 'p-1', secondary: 'Amul' },
];

function Where() {
  const location = useLocation();
  return <p data-testid="where">{location.pathname + location.search}</p>;
}

function Harness({ onSubmit }: { onSubmit: (v: string) => void }) {
  const [value, setValue] = useState('');
  return (
    <>
      <SearchBar id="t" value={value} onChange={setValue} onSubmit={onSubmit} />
      <Where />
    </>
  );
}

function renderBar(onSubmit = vi.fn()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="*" element={<Harness onSubmit={onSubmit} />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return onSubmit;
}

async function typeAndWait(text: string) {
  const input = screen.getByRole('combobox');
  fireEvent.focus(input);
  fireEvent.change(input, { target: { value: text } });
  await act(async () => {
    await new Promise((r) => setTimeout(r, 200));
  });
  await screen.findByText('Amul Butter');
  return input;
}

describe('SearchBar typeahead', () => {
  beforeEach(() => {
    getSuggestions.mockReset();
    getSuggestions.mockResolvedValue(SUGGESTIONS);
  });

  it('shows brand, category and product suggestions as a listbox', async () => {
    renderBar();
    const input = await typeAndWait('amu');
    expect(input.getAttribute('aria-expanded')).toBe('true');
    expect(screen.getAllByRole('option')).toHaveLength(3);
    expect(screen.getByText('Brand · 45 products')).toBeTruthy();
    expect(getSuggestions).toHaveBeenCalledWith('amu');
  });

  it('arrow keys move the highlight and Enter opens a product', async () => {
    renderBar();
    const input = await typeAndWait('amu');
    fireEvent.keyDown(input, { key: 'ArrowDown' });
    fireEvent.keyDown(input, { key: 'ArrowDown' });
    fireEvent.keyDown(input, { key: 'ArrowDown' });
    expect(screen.getAllByRole('option')[2]?.getAttribute('aria-selected')).toBe('true');
    fireEvent.submit(input.closest('form') as HTMLFormElement);
    expect(screen.getByTestId('where').textContent).toBe('/product/p-1');
  });

  it('a category suggestion opens the category', async () => {
    renderBar();
    const input = await typeAndWait('amu');
    fireEvent.keyDown(input, { key: 'ArrowDown' });
    fireEvent.keyDown(input, { key: 'ArrowDown' });
    fireEvent.submit(input.closest('form') as HTMLFormElement);
    expect(screen.getByTestId('where').textContent).toBe('/search?category=DAIRY');
  });

  it('Enter with nothing highlighted searches the typed words; Escape closes the list', async () => {
    const onSubmit = renderBar();
    const input = await typeAndWait('amu');
    fireEvent.keyDown(input, { key: 'Escape' });
    expect(input.getAttribute('aria-expanded')).toBe('false');
    fireEvent.submit(input.closest('form') as HTMLFormElement);
    expect(onSubmit).toHaveBeenCalledWith('amu');
  });

  it('picking a brand searches for it', async () => {
    const onSubmit = renderBar();
    await typeAndWait('amu');
    fireEvent.mouseDown(screen.getAllByRole('option')[0] as HTMLElement);
    expect(onSubmit).toHaveBeenCalledWith('Amul');
  });

  it('asks for nothing under two characters', async () => {
    renderBar();
    const input = screen.getByRole('combobox');
    fireEvent.focus(input);
    fireEvent.change(input, { target: { value: 'a' } });
    await act(async () => {
      await new Promise((r) => setTimeout(r, 200));
    });
    expect(getSuggestions).not.toHaveBeenCalled();
    expect(screen.queryAllByRole('option')).toHaveLength(0);
  });
});
