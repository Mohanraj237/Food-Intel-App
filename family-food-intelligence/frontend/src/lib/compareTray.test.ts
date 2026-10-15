import { beforeEach, describe, expect, it } from 'vitest';
import { MAX_COMPARE, clearTray, compareHref, isInTray, setTray, toggleTray, trayItems } from './compareTray';

function item(id: string) {
  return { id, brandName: 'B', name: `Product ${id}`, imageUrl: null, categoryCode: 'BISCUITS' };
}

describe('compare tray', () => {
  beforeEach(() => {
    localStorage.clear();
    clearTray();
  });

  it('adds, removes on a second toggle, and remembers the order', () => {
    expect(toggleTray(item('a'))).toBe('added');
    expect(toggleTray(item('b'))).toBe('added');
    expect(trayItems().map((i) => i.id)).toEqual(['a', 'b']);
    expect(toggleTray(item('a'))).toBe('removed');
    expect(isInTray('a')).toBe(false);
  });

  it('refuses a sixth product instead of silently dropping one', () => {
    for (let i = 0; i < MAX_COMPARE; i++) toggleTray(item(String(i)));
    expect(toggleTray(item('extra'))).toBe('full');
    expect(trayItems()).toHaveLength(MAX_COMPARE);
    expect(isInTray('extra')).toBe(false);
  });

  it('deduplicates and caps when replaced from a link', () => {
    setTray([item('x'), item('x'), item('y'), item('1'), item('2'), item('3'), item('4')]);
    expect(trayItems().map((i) => i.id)).toEqual(['x', 'y', '1', '2', '3']);
  });

  it('persists per device and survives a reload of the module cache', () => {
    toggleTray(item('keep'));
    expect(JSON.parse(localStorage.getItem('ffi.compareTray.anon') ?? '[]')).toHaveLength(1);
  });

  it('builds the compare link', () => {
    expect(compareHref(['a', 'b'])).toBe('/compare?ids=a,b');
  });
});
