import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { corsHeaders } from "../_shared/cors.ts";
import { executeBufferPost } from "../_shared/buffer.ts";

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const body = await req.json();
    const {
      channel_id,
      title,
      description = "",
      hashtags = "",
      media_urls = [],
      media_type = "image",
      mode = "addToQueue",
      due_at,
      upload_request_id,
    } = body;

    if (!channel_id || !title) {
      return new Response(
        JSON.stringify({ error: "Missing required fields: channel_id and title" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const postResult = await executeBufferPost({
      channelId: channel_id,
      title,
      description,
      hashtags: Array.isArray(hashtags) ? hashtags.join(" ") : hashtags,
      mediaUrls: Array.isArray(media_urls) ? media_urls : [media_urls],
      mediaType: media_type === "video" ? "video" : "image",
      mode,
      dueAt: due_at,
      uploadRequestId: upload_request_id,
    });

    return new Response(JSON.stringify(postResult), {
      status: 200,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (err: any) {
    return new Response(
      JSON.stringify({ error: err.message || "Failed to post to Buffer" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
