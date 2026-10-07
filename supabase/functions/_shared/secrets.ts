import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";

export function getServiceSupabase() {
  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  if (!supabaseUrl || !serviceRoleKey) {
    throw new Error("Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY");
  }
  return createClient(supabaseUrl, serviceRoleKey);
}

export async function getAppSecret(name: string): Promise<string> {
  const supabase = getServiceSupabase();
  const { data, error } = await supabase
    .from("app_secrets")
    .select("value")
    .eq("name", name)
    .single();

  if (error || !data || !data.value) {
    throw new Error(`Required secret '${name}' not found in app_secrets`);
  }
  return data.value;
}
