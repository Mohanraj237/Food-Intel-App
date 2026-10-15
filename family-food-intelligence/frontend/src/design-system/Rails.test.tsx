import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { CategoryGrid, ProductRail } from './Rails';
import { ToastProvider } from './Toast';
import type { ProductCardData } from './ProductCard';

const CATEGORIES = [
  { code: 'BISCUITS', name: 'Biscuits and cookies' },
  { code: 'BEVERAGES', name: 'Packaged beverages' },
  { code: 'CHIPS', name: 'Chips and wafers' },
];

function renderWithProviders(ui: React.ReactElement) {
  return render(
    <MemoryRouter>
      <ToastProvider>{ui}</ToastProvider>
    </MemoryRouter>,
  );
}

describe('CategoryGrid', () => {
  it('renders every category as a tile linking to its search filter — a grid, never a scroller (D-074)', () => {
    renderWithProviders(<CategoryGrid categories={CATEGORIES} />);

    const list = screen.getByRole('list');
    const items = within(list).getAllByRole('listitem');
    expect(items).toHaveLength(CATEGORIES.length);

    // role="listitem" is not a name-from-contents role, so the tile is found by its
    // visible label text and the link target asserted on the enclosing anchor.
    const biscuits = screen.getByText('Biscuits and cookies').closest('a');
    expect(biscuits).toHaveAttribute('href', '/category/BISCUITS');
  });

  it('accepts a custom link target, e.g. straight into filtered search', () => {
    renderWithProviders(<CategoryGrid categories={CATEGORIES} linkTo={(code) => `/search?category=${code}`} />);

    const chips = screen.getByText('Chips and wafers').closest('a');
    expect(chips).toHaveAttribute('href', '/search?category=CHIPS');
  });
});

const PRODUCTS: ProductCardData[] = Array.from({ length: 6 }, (_, i) => ({
  id: `p${i}`,
  brandName: 'Parle',
  name: `Product ${i}`,
}));

describe('ProductRail (carousel)', () => {
  it('renders one product per list item, with previous/next controls for a pointer', () => {
    renderWithProviders(<ProductRail products={PRODUCTS} />);

    const list = screen.getByRole('list');
    expect(within(list).getAllByRole('listitem')).toHaveLength(PRODUCTS.length);
    expect(screen.getByRole('button', { name: 'Previous' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Next' })).toBeInTheDocument();
  });

  it('disables Previous at the start and enables Next once there is more to scroll to', () => {
    renderWithProviders(<ProductRail products={PRODUCTS} />);

    const scroller = screen.getByRole('list');
    // jsdom never computes real layout — stand in for a rail wider than its viewport.
    Object.defineProperty(scroller, 'scrollLeft', { value: 0, writable: true, configurable: true });
    Object.defineProperty(scroller, 'scrollWidth', { value: 1200, configurable: true });
    Object.defineProperty(scroller, 'clientWidth', { value: 320, configurable: true });
    fireEvent.scroll(scroller);

    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Next' })).toBeEnabled();
  });

  it('enables Previous and disables Next once scrolled to the end', () => {
    renderWithProviders(<ProductRail products={PRODUCTS} />);

    const scroller = screen.getByRole('list');
    Object.defineProperty(scroller, 'scrollWidth', { value: 1200, configurable: true });
    Object.defineProperty(scroller, 'clientWidth', { value: 320, configurable: true });
    Object.defineProperty(scroller, 'scrollLeft', { value: 880, writable: true, configurable: true });
    fireEvent.scroll(scroller);

    expect(screen.getByRole('button', { name: 'Previous' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();
  });

  it('scrolls the rail by roughly one page when Next is clicked', () => {
    renderWithProviders(<ProductRail products={PRODUCTS} />);

    const scroller = screen.getByRole('list');
    Object.defineProperty(scroller, 'scrollWidth', { value: 1200, configurable: true });
    Object.defineProperty(scroller, 'clientWidth', { value: 320, configurable: true });
    Object.defineProperty(scroller, 'scrollLeft', { value: 0, writable: true, configurable: true });
    fireEvent.scroll(scroller);

    const scrollBy = vi.fn();
    scroller.scrollBy = scrollBy as typeof scroller.scrollBy;

    fireEvent.click(screen.getByRole('button', { name: 'Next' }));

    expect(scrollBy).toHaveBeenCalledTimes(1);
    const call = scrollBy.mock.calls[0]?.[0] as ScrollToOptions;
    expect(call.left).toBeCloseTo(320 * 0.9);
  });

  it('shows a skeleton, not an empty carousel, while loading', () => {
    renderWithProviders(<ProductRail products={[]} loading skeletonCount={3} />);
    expect(screen.queryByRole('button', { name: 'Next' })).not.toBeInTheDocument();
  });
});
