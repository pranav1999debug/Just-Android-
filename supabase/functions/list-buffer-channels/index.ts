import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { corsHeaders } from "../_shared/cors.ts";
import { getAppSecret } from "../_shared/secrets.ts";

interface ChannelInfo {
  id: string;
  name: string;
  displayName: string;
  service: string;
  avatar?: string;
  characterLimit?: number;
}

// Known default character limits by Buffer service
const SERVICE_CHARACTER_LIMITS: Record<string, number> = {
  twitter: 280,
  x: 280,
  bluesky: 300,
  threads: 500,
  pinterest: 500,
  instagram: 2196,
  tiktok: 2200,
  linkedin: 3000,
  facebook: 5000,
  googlebusiness: 4000,
  mastodon: 500,
  youtube: 5000,
};

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const bufferApiKey = await getAppSecret("buffer_api_key");

    // Buffer GraphQL GetChannels query
    const graphqlQuery = `
      query GetChannels {
        account {
          organizations {
            id
            name
            channels {
              id
              name
              displayName
              service
              avatar
            }
          }
        }
      }
    `;

    let bufferRes = await fetch("https://api.buffer.com", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${bufferApiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ query: graphqlQuery }),
    });

    let bufferData = await bufferRes.json();
    let channels: ChannelInfo[] = [];

    if (bufferData.data?.account?.organizations) {
      for (const org of bufferData.data.account.organizations) {
        if (Array.isArray(org.channels)) {
          for (const ch of org.channels) {
            const serviceKey = (ch.service || "").toLowerCase();
            channels.push({
              id: ch.id,
              name: ch.name || ch.displayName || "Buffer Channel",
              displayName: ch.displayName || ch.name || "Buffer Channel",
              service: ch.service || "unknown",
              avatar: ch.avatar,
              characterLimit: SERVICE_CHARACTER_LIMITS[serviceKey] ?? 2000,
            });
          }
        }
      }
    } else {
      // Fallback query if account.organizations isn't available
      const fallbackQuery = `
        query GetChannelsFallback {
          channels {
            id
            name
            displayName
            service
            avatar
          }
        }
      `;
      const fallbackRes = await fetch("https://api.buffer.com", {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${bufferApiKey}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ query: fallbackQuery }),
      });
      const fallbackData = await fallbackRes.json();
      if (Array.isArray(fallbackData.data?.channels)) {
        channels = fallbackData.data.channels.map((ch: any) => {
          const serviceKey = (ch.service || "").toLowerCase();
          return {
            id: ch.id,
            name: ch.name || ch.displayName || "Buffer Channel",
            displayName: ch.displayName || ch.name || "Buffer Channel",
            service: ch.service || "unknown",
            avatar: ch.avatar,
            characterLimit: SERVICE_CHARACTER_LIMITS[serviceKey] ?? 2000,
          };
        });
      }
    }

    if (bufferData.errors && channels.length === 0) {
      const errMsg = bufferData.errors.map((e: any) => e.message).join(", ");
      return new Response(
        JSON.stringify({ error: `Buffer GraphQL error: ${errMsg}` }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    return new Response(
      JSON.stringify({
        success: true,
        channels,
      }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (err: any) {
    return new Response(
      JSON.stringify({ error: err.message || "Internal server error" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
