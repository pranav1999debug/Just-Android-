import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { corsHeaders } from "../_shared/cors.ts";
import { getAppSecret, getServiceSupabase } from "../_shared/secrets.ts";
import { executeBufferPost } from "../_shared/buffer.ts";

const MAX_VIDEO_BYTES = 50 * 1024 * 1024; // 50MB video limit to respect edge memory

function getMediaType(file: File): "image" | "video" {
  const mime = (file.type || "").toLowerCase().trim();
  if (mime.startsWith("image/")) return "image";
  if (mime.startsWith("video/")) return "video";

  const name = (file.name || "").toLowerCase();
  const ext = name.split(".").pop() || "";
  if (["jpg", "jpeg", "png", "webp", "gif", "heic", "bmp", "avif"].includes(ext)) {
    return "image";
  }
  if (["mp4", "mov", "webm", "mkv", "m4v", "avi", "ts"].includes(ext)) {
    return "video";
  }
  // Safe default fallback
  return "image";
}

async function uploadToImgchest(
  files: File[],
  apiKey: string,
  title: string
): Promise<string[]> {
  const urls: string[] = [];
  const BATCH_SIZE = 20;

  for (let i = 0; i < files.length; i += BATCH_SIZE) {
    const batch = files.slice(i, i + BATCH_SIZE);
    const formData = new FormData();

    if (i === 0) {
      if (title.trim()) {
        formData.append("title", title.trim());
      }
      for (const f of batch) {
        formData.append("images[]", f, f.name);
      }

      const res = await fetch("https://api.imgchest.com/v1/post", {
        method: "POST",
        headers: {
          Authorization: `Bearer ${apiKey}`,
        },
        body: formData,
      });

      if (!res.ok) {
        const errText = await res.text();
        throw new Error(`Imgchest error (HTTP ${res.status}): ${errText}`);
      }

      const resJson = await res.json();
      const images = resJson.data?.images ?? [];
      for (const img of images) {
        if (img.link) urls.push(img.link);
      }

      const postId = resJson.data?.id;
      if (postId && i + BATCH_SIZE < files.length) {
        for (let j = i + BATCH_SIZE; j < files.length; j += BATCH_SIZE) {
          const nextBatch = files.slice(j, j + BATCH_SIZE);
          const addFormData = new FormData();
          for (const f of nextBatch) {
            addFormData.append("images[]", f, f.name);
          }
          const addRes = await fetch(`https://api.imgchest.com/v1/post/${postId}/add`, {
            method: "POST",
            headers: {
              Authorization: `Bearer ${apiKey}`,
            },
            body: addFormData,
          });
          if (!addRes.ok) {
            const errText = await addRes.text();
            throw new Error(`Imgchest add error (HTTP ${addRes.status}): ${errText}`);
          }
          const addJson = await addRes.json();
          const addedImages = addJson.data?.images ?? [];
          for (const img of addedImages) {
            if (img.link) urls.push(img.link);
          }
        }
        break;
      }
    }
  }

  if (urls.length === 0) {
    throw new Error("Imgchest error: No image URLs returned from host");
  }

  return urls;
}

async function uploadToCatbox(
  files: File[],
  userhash: string
): Promise<string[]> {
  const urls: string[] = [];

  for (const f of files) {
    if (f.size > MAX_VIDEO_BYTES) {
      throw new Error("Video too large, max 50MB");
    }

    const formData = new FormData();
    formData.append("reqtype", "fileupload");
    formData.append("userhash", userhash);
    formData.append("fileToUpload", f, f.name);

    const res = await fetch("https://catbox.moe/user/api.php", {
      method: "POST",
      headers: {
        "User-Agent": "Mozilla/5.0",
      },
      body: formData,
    });

    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`Catbox error (HTTP ${res.status}): ${errText}`);
    }

    const urlText = (await res.text()).trim();
    if (!urlText.startsWith("http://") && !urlText.startsWith("https://")) {
      throw new Error(`Catbox error (HTTP ${res.status}): ${urlText}`);
    }

    urls.push(urlText);
  }

  return urls;
}

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const contentType = req.headers.get("content-type") || "";
    if (!contentType.includes("multipart/form-data")) {
      return new Response(
        JSON.stringify({ error: "Expected multipart/form-data request" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const formData = await req.formData();
    const files: File[] = [];

    for (const [_, value] of formData.entries()) {
      if (value instanceof File && value.size > 0) {
        files.push(value);
      }
    }

    if (files.length === 0) {
      return new Response(
        JSON.stringify({ error: "No files provided for upload" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const title = (formData.get("title") as string) || "";
    const description = (formData.get("description") as string) || "";
    const hashtags = (formData.get("hashtags") as string) || "";
    const channelId = (formData.get("channel_id") as string) || "";
    const mode = (formData.get("mode") as string) || "addToQueue";
    const shareParam = formData.get("share");
    const shareToSocial =
      shareParam === "true" || shareParam === "1" || Boolean(channelId && shareParam !== "false");
    const userId = (formData.get("user_id") as string) || null;

    const imageFiles: File[] = [];
    const videoFiles: File[] = [];

    for (const f of files) {
      const mediaType = getMediaType(f);
      if (mediaType === "image") {
        imageFiles.push(f);
      } else {
        videoFiles.push(f);
      }
    }

    const groups: {
      type: "image" | "video";
      host: "imgchest" | "catbox";
      files: File[];
    }[] = [];

    if (imageFiles.length > 0) {
      groups.push({ type: "image", host: "imgchest", files: imageFiles });
    }
    if (videoFiles.length > 0) {
      groups.push({ type: "video", host: "catbox", files: videoFiles });
    }

    const supabase = getServiceSupabase();
    const results: any[] = [];
    let lastError: string | null = null;

    for (const group of groups) {
      const rowId = crypto.randomUUID();

      const { error: insertError } = await supabase.from("upload_requests").insert({
        id: rowId,
        user_id: userId,
        host: group.host,
        status: "uploading",
        file_count: group.files.length,
        image_urls: [],
        media_type: group.type,
        title: title || null,
        description: description || null,
        caption: title || description || null,
        hashtags: hashtags || null,
        share_to_social: shareToSocial,
        buffer_channel_id: channelId || null,
        created_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
      });

      if (insertError) {
        console.error("Failed to insert initial upload_requests row:", insertError);
      }

      let uploadedUrls: string[] = [];
      let bufferPostId: string | null = null;
      let uploadError: string | null = null;

      try {
        if (group.host === "imgchest") {
          const imgchestKey = await getAppSecret("imgchest_api_key");
          uploadedUrls = await uploadToImgchest(group.files, imgchestKey, title);
        } else {
          const catboxHash = await getAppSecret("catbox_userhash");
          uploadedUrls = await uploadToCatbox(group.files, catboxHash);
        }

        if (shareToSocial && channelId && title.trim().length > 0) {
          try {
            const bufferRes = await executeBufferPost({
              channelId,
              title,
              description,
              hashtags,
              mediaUrls: uploadedUrls,
              mediaType: group.type,
              mode,
              uploadRequestId: rowId,
            });
            bufferPostId = bufferRes.buffer_post_id;
          } catch (postErr: any) {
            console.error("Buffer post failed:", postErr);
            uploadError = `Media uploaded, but Buffer posting failed: ${postErr.message}`;
          }
        }

        await supabase
          .from("upload_requests")
          .update({
            status: uploadError ? "failed" : "done",
            image_urls: uploadedUrls,
            buffer_post_id: bufferPostId,
            error: uploadError,
            updated_at: new Date().toISOString(),
          })
          .eq("id", rowId);

        results.push({
          upload_request_id: rowId,
          host: group.host,
          media_type: group.type,
          file_count: group.files.length,
          urls: uploadedUrls,
          status: uploadError ? "partial" : "done",
          buffer_post_id: bufferPostId,
          error: uploadError,
        });
      } catch (err: any) {
        const errorMsg = err.message || "Upload failed";
        lastError = errorMsg;
        await supabase
          .from("upload_requests")
          .update({
            status: "failed",
            error: errorMsg,
            updated_at: new Date().toISOString(),
          })
          .eq("id", rowId);

        results.push({
          upload_request_id: rowId,
          host: group.host,
          media_type: group.type,
          status: "failed",
          error: errorMsg,
        });
      }
    }

    const hasAnySuccess = results.some((r) => r.urls && r.urls.length > 0);
    if (!hasAnySuccess && lastError) {
      return new Response(
        JSON.stringify({
          error: lastError,
          results,
        }),
        {
          status: 400,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        }
      );
    }

    return new Response(
      JSON.stringify({
        success: hasAnySuccess,
        results,
        error: lastError,
      }),
      {
        status: hasAnySuccess ? 200 : 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      }
    );
  } catch (err: any) {
    return new Response(
      JSON.stringify({ error: err.message || "Unexpected server error" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
