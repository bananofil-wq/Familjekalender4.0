import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "content-type, x-family-code",
};

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405, headers: cors });

  try {
    const familyCode = (req.headers.get("x-family-code") ?? "").trim().toUpperCase();
    if (!familyCode) return json({ error: "Missing family code" }, 401);

    const { family_id, title, body, dedupe_key, sender_device_id, target_member_id } = await req.json();
    if (!family_id || !title) return json({ error: "Missing family_id or title" }, 400);

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

    const verify = await fetch(
      `${supabaseUrl}/rest/v1/families?id=eq.${encodeURIComponent(family_id)}&join_code=eq.${encodeURIComponent(familyCode)}&select=id&limit=1`,
      { headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}` } },
    );
    const rows = await verify.json();
    if (!verify.ok || !Array.isArray(rows) || rows.length === 0) {
      return json({ error: "Family authorization failed" }, 403);
    }

    let normalizedBody = String(body || title).trim();
    if (/^Ny inköpsvara\s*:/i.test(normalizedBody)) {
      const rest = normalizedBody.replace(/^Ny inköpsvara\s*:\s*/i, "").trim();
      normalizedBody = rest ? `Ny inköpsvara:\n\n${rest}` : "Ny inköpsvara:";
    }

    const stableKey = String(dedupe_key || "").trim();
    const targetMemberId = String(target_member_id || "").trim();
    const dedupeMaterial = stableKey
      ? `${family_id}\n${targetMemberId}\n${stableKey}`
      : `${family_id}\n${targetMemberId}\n${String(title).trim()}\n${normalizedBody}\n${Math.floor(Date.now() / (5 * 60 * 1000))}`;
    const fingerprint = await sha256Hex(dedupeMaterial);

    if (!targetMemberId) {
      const claim = await fetch(`${supabaseUrl}/rest/v1/rpc/claim_push_dedupe`, {
        method: "POST",
        headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}`, "Content-Type": "application/json" },
        body: JSON.stringify({ p_key: `family-push:${fingerprint}` }),
      });
      if (!claim.ok) return json({ error: "Could not reserve notification", details: await claim.text() }, 500);
      if (await claim.json() !== true) return json({ ok: true, deduplicated: true }, 200);
    }

    const oneSignalKey = Deno.env.get("ONESIGNAL_REST_API_KEY");
    if (!oneSignalKey) return json({ error: "Push provider is not configured yet" }, 503);

    const senderDeviceId = String(sender_device_id || "").trim();
    const filters: Record<string, string>[] = [
      { field: "tag", key: "family_id", relation: "=", value: family_id },
    ];
    if (targetMemberId) {
      filters.push({ field: "tag", key: "member_id", relation: "=", value: targetMemberId });
    }
    if (senderDeviceId) {
      filters.push({ field: "tag", key: "device_id", relation: "!=", value: senderDeviceId });
    }

    // Send calendar alerts through a high-importance channel only on devices
    // that have advertised it. Older installations keep their existing channel.
    const supportsChannel = stableKey.startsWith("calendar:") || stableKey.startsWith("calendar-series:");
    const groups = supportsChannel ? [
      { name: "high_priority", filters: [...filters, { field: "tag", key: "family_push_channel_v1", relation: "=", value: "1" }], channel: "family_activity_alerts_v1" },
      { name: "legacy", filters: [...filters, { field: "tag", key: "family_push_channel_v1", relation: "not_exists" }], channel: null },
    ] : [{ name: "default", filters, channel: null }];

    const results = await Promise.all(groups.map(async (group) => {
      const push = await fetch("https://api.onesignal.com/notifications", {
        method: "POST",
        headers: {
          Authorization: `Key ${oneSignalKey}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
        app_id: "220bbe78-fd9a-4e5a-8d57-b84399821f7c",
        headings: { sv: "Familjekalendern", en: "Familjekalendern" },
        contents: { sv: normalizedBody, en: normalizedBody },
        filters: group.filters,
        data: {
          family_id,
          dedupe_key: stableKey || null,
          sender_device_id: senderDeviceId || null,
          ...(stableKey.startsWith("calendar:") || stableKey.startsWith("calendar-series:")
            ? {
                type: "calendar_event_created",
                event_id: stableKey.startsWith("calendar:") ? stableKey.slice("calendar:".length) : "",
                series_id: stableKey.startsWith("calendar-series:") ? stableKey.slice("calendar-series:".length) : null,
              }
            : {}),
        },
          ...(group.channel ? { existing_android_channel_id: group.channel } : {}),
          priority: 10,
          android_visibility: 1,
          ttl: 86400,
        }),
      });
      const responseText = await push.text();
      if (!push.ok) {
        console.error("OneSignal rejected family push", group.name, push.status, responseText);
      }
      return { name: group.name, ok: push.ok, status: push.status, result: responseText };
    }));
    if (results.some((result) => !result.ok)) {
      return json({ error: "OneSignal rejected one or more notification groups", groups: results.map(({name, ok, status}) => ({name, ok, status})) }, 502);
    }
    return json({ ok: true, groups: results.map(({name, status}) => ({name, status})) }, 200);
  } catch (error) {
    return json({ error: error instanceof Error ? error.message : "Unknown error" }, 500);
  }
});

async function sha256Hex(value: string) {
  const data = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function json(value: unknown, status: number) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { ...cors, "Content-Type": "application/json" },
  });
}
