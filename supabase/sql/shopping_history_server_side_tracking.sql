-- Exactly-once shopping purchase statistics for v4.3+ clients.
-- Old clients still record purchases via the existing REST RPC, so this
-- trigger runs ONLY when the app supplies x-shopping-history-tracking=server.
-- No historical purchases are modified during migration.
create or replace function public.record_new_shopping_check()
returns trigger
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
declare
  v_headers jsonb;
  v_name text;
begin
  v_headers := coalesce(nullif(current_setting('request.headers', true), '')::jsonb, '{}'::jsonb);
  if coalesce(v_headers->>'x-shopping-history-tracking', '') <> 'server' then
    return new;
  end if;
  if old.checked is false and new.checked is true then
    v_name := trim(lower(regexp_replace(new.name, '[^A-Za-z0-9ÅÄÖåäö]+', ' ', 'g')));
    if v_name <> '' then
      perform public.record_shopping_purchase(new.family_id, trim(new.name), v_name);
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists shopping_items_record_new_check on public.shopping_items;
create trigger shopping_items_record_new_check
after update of checked on public.shopping_items
for each row
when (old.checked is distinct from new.checked)
execute function public.record_new_shopping_check();

revoke all on function public.record_new_shopping_check() from public;
