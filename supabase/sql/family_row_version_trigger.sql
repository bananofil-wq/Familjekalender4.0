-- Keep a reliable row revision for optimistic concurrent family edits.
-- This function never bypasses RLS. Existing family data is unchanged.
create or replace function public.touch_family_row_updated_at()
returns trigger
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
begin
    new.updated_at := clock_timestamp();
    return new;
end;
$$;

drop trigger if exists calendar_events_touch_updated_at on public.calendar_events;
create trigger calendar_events_touch_updated_at
before update on public.calendar_events
for each row execute function public.touch_family_row_updated_at();

drop trigger if exists shopping_items_touch_updated_at on public.shopping_items;
create trigger shopping_items_touch_updated_at
before update on public.shopping_items
for each row execute function public.touch_family_row_updated_at();

drop trigger if exists todo_items_touch_updated_at on public.todo_items;
create trigger todo_items_touch_updated_at
before update on public.todo_items
for each row execute function public.touch_family_row_updated_at();

revoke all on function public.touch_family_row_updated_at() from public;
