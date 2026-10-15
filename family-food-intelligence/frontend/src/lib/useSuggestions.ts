import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getSuggestions, type Suggestion } from './catalog';

const DEBOUNCE_MS = 150;
const MIN_LENGTH = 2;

/**
 * Search-box suggestions for what has been typed, debounced so each keystroke does not fire
 * a request. Public catalogue data only; nothing about the person is sent or kept.
 */
export function useSuggestions(value: string, enabled: boolean): { items: Suggestion[]; loading: boolean } {
  const [debounced, setDebounced] = useState(value.trim());

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value.trim()), DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [value]);

  const active = enabled && debounced.length >= MIN_LENGTH;
  const query = useQuery({
    queryKey: ['suggest', debounced.toLowerCase()],
    queryFn: () => getSuggestions(debounced),
    enabled: active,
    staleTime: 5 * 60 * 1000,
    retry: false,
  });

  return { items: active ? (query.data ?? []) : [], loading: active && query.isFetching };
}
