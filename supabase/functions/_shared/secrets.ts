import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";

export function getServiceSupabase() {
  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  if (!supabaseUrl || !serviceRoleKey) {
    throw new Error("Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY");
  }
  return createClient(supabaseUrl, serviceRoleKey);
}

const secretCache = new Map<string, string>();
let initialLoadDone = false;

async function loadAllSecrets(): Promise<void> {
  const supabase = getServiceSupabase();
  const { data, error } = await supabase
    .from("app_secrets")
    .select("name, value");

  if (error) {
    throw new Error(`Failed to load app_secrets from database: ${error.message}`);
  }

  if (data) {
    for (const row of data) {
      if (row.name && row.value) {
        secretCache.set(row.name, row.value);
      }
    }
  }
  initialLoadDone = true;
}

export async function getAppSecret(name: string): Promise<string> {
  if (!initialLoadDone) {
    await loadAllSecrets();
  }

  if (secretCache.has(name)) {
    return secretCache.get(name)!;
  }

  // Reload once if the name is missing
  await loadAllSecrets();
  if (secretCache.has(name)) {
    return secretCache.get(name)!;
  }

  throw new Error(`Required secret '${name}' not found in app_secrets`);
}

