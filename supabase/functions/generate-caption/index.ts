import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { corsHeaders } from "../_shared/cors.ts";
import { getAppSecret } from "../_shared/secrets.ts";

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const { title, variation } = await req.json();
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

    const angles = [
      "Velvet & Late-Night Intrigue — sultry, magnetic, and effortlessly confident",
      "High-Voltage Glamour — bold, unapologetic, and impossible to scroll past",
      "Playful Tease & Siren Energy — witty, seductive, and leaving just enough to the imagination",
      "Editorial Luxury & Heat — sleek after-dark sophistication with a spicy edge",
      "Behind-Closed-Doors Exclusive — intimate, raw, and dangerously captivating"
    ];
    const chosenAngle = angles[Math.abs(Number(variation || Date.now())) % angles.length];

    const prompt = `You are an elite, high-converting creative copywriter for an exclusive glamour creator platform.
Given the post title: "${title.trim()}"
Creative Angle: ${chosenAngle}

Write:
1. A SHORT, SPICY, teasing, and magnetic description (1 to 2 punchy sentences, max 32 words) written like a professional human writer. Never use boring clichés like "Explore exclusive" or "Dive into".
2. 5 to 7 trending, relevant hashtags tailored to "${title.trim()}".

You must respond with ONLY valid JSON with this exact structure:
{
  "description": "Short, spicy professional copywriter description here",
  "hashtags": ["#Tag1", "#Tag2", "#Tag3", "#Tag4", "#Tag5"]
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
            content: "You are an elite glamour & creator copywriter. Output ONLY valid JSON with 'description' and 'hashtags' keys. No explanation, no markdown backticks.",
          },
          {
            role: "user",
            content: prompt,
          },
        ],
        response_format: { type: "json_object" },
        temperature: 0.92,
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

    const description = parsed.description || "";
    const hashtags = Array.isArray(parsed.hashtags)
      ? parsed.hashtags.map((h) => (h.startsWith("#") ? h : `#${h}`))
      : ["#Exclusive", "#SpicyDrop", "#AfterHours"];

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
