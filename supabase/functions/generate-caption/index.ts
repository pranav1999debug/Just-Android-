import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { corsHeaders } from "../_shared/cors.ts";
import { getAppSecret } from "../_shared/secrets.ts";

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const { title } = await req.json();
    if (!title || typeof title !== "string" || title.trim().length === 0) {
      return new Response(
        JSON.stringify({ error: "Missing or invalid 'title' parameter" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const groqApiKey = await getAppSecret("groq_api_key");
    let groqModel = "llama-3.3-70b-versatile";
    try {
      const modelVal = await getAppSecret("groq_model");
      if (modelVal && modelVal.trim().length > 0) {
        groqModel = modelVal.trim();
      }
    } catch (_) {
      // Fallback to default Groq model if not specified
    }

    const prompt = `You are a social media specialist. Given the post title: "${title.trim()}", generate an engaging social media description and 3-5 trending hashtags.
You must respond with ONLY valid JSON with this exact structure:
{
  "description": "Engaging description text here",
  "hashtags": ["#tag1", "#tag2", "#tag3"]
}`;

    const groqRes = await fetch("https://api.groq.com/openai/v1/chat/completions", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${groqApiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: groqModel,
        messages: [
          {
            role: "system",
            content: "You are a social media copywriter. Output ONLY valid JSON with 'description' and 'hashtags' keys. No explanation, no markdown backticks.",
          },
          {
            role: "user",
            content: prompt,
          },
        ],
        response_format: { type: "json_object" },
        temperature: 0.7,
      }),
    });

    if (!groqRes.ok) {
      const errText = await groqRes.text();
      return new Response(
        JSON.stringify({ error: `Groq API error (${groqRes.status}): ${errText}` }),
        { status: groqRes.status, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const groqData = await groqRes.json();
    const rawContent = groqData.choices?.[0]?.message?.content ?? "{}";

    let parsed: { description?: string; hashtags?: string[] } = {};
    try {
      parsed = JSON.parse(rawContent);
    } catch (_) {
      const jsonMatch = rawContent.match(/\{[\s\S]*\}/);
      if (jsonMatch) {
        parsed = JSON.parse(jsonMatch[0]);
      } else {
        throw new Error("Failed to parse JSON response from Groq");
      }
    }

    const description = parsed.description || `Check out ${title}!`;
    const hashtags = Array.isArray(parsed.hashtags)
      ? parsed.hashtags.map((h) => (h.startsWith("#") ? h : `#${h}`))
      : ["#JustFan", "#Exclusive", "#Trending"];

    return new Response(
      JSON.stringify({
        success: true,
        description,
        hashtags,
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
