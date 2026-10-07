import { getAppSecret, getServiceSupabase } from "./secrets.ts";

export const SERVICE_CHARACTER_LIMITS: Record<string, number> = {
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

export async function executeBufferPost({
  channelId,
  title,
  description,
  hashtags,
  mediaUrls,
  mediaType,
  mode,
  dueAt,
  uploadRequestId,
}: {
  channelId: string;
  title: string;
  description: string;
  hashtags: string;
  mediaUrls: string[];
  mediaType: "image" | "video";
  mode?: string;
  dueAt?: string;
  uploadRequestId?: string;
}) {
  const bufferApiKey = await getAppSecret("buffer_api_key");
  let appDownloadUrl = "https://github.com/pranav1999debug/Just-Android-/releases/latest/download/app-debug.apk";
  try {
    const customUrl = await getAppSecret("app_download_url");
    if (customUrl && customUrl.trim().length > 0) {
      appDownloadUrl = customUrl.trim();
    }
  } catch (_) {
    // Keep default app_download_url
  }

  // Format hashtags
  let formattedHashtags = hashtags.trim();
  if (formattedHashtags.length > 0 && !formattedHashtags.startsWith("#")) {
    formattedHashtags = formattedHashtags
      .split(/\s+/)
      .map((tag) => (tag.startsWith("#") ? tag : `#${tag}`))
      .join(" ");
  }

  // Determine character limit
  let charLimit = 2000;
  for (const [key, limit] of Object.entries(SERVICE_CHARACTER_LIMITS)) {
    if (channelId.toLowerCase().includes(key)) {
      charLimit = limit;
      break;
    }
  }

  // Post text structure:
  // {title}\n\n{description}\n\n{hashtags}\n\nDownload the app: {app_download_url}
  const staticParts = `${title.trim()}\n\n\n\n${formattedHashtags}\n\nDownload the app: ${appDownloadUrl}`;
  const staticLength = staticParts.length;

  let finalDescription = description.trim();
  let wasTrimmed = false;

  const totalLength = title.trim().length + 2 + finalDescription.length + 2 + formattedHashtags.length + 2 + `Download the app: ${appDownloadUrl}`.length;

  if (totalLength > charLimit) {
    const maxDescLength = Math.max(10, charLimit - staticLength - 5);
    if (finalDescription.length > maxDescLength) {
      finalDescription = finalDescription.substring(0, maxDescLength).trim() + "...";
      wasTrimmed = true;
    }
  }

  const postText = `${title.trim()}\n\n${finalDescription}\n\n${formattedHashtags}\n\nDownload the app: ${appDownloadUrl}`;

  // Build assets array according to mediaType
  const assets: any[] = [];
  if (mediaType === "video") {
    for (const url of mediaUrls) {
      assets.push({ video: { url } });
    }
  } else {
    for (const url of mediaUrls) {
      assets.push({ image: { url } });
    }
  }

  let sharingMode = "addToQueue";
  if (mode === "shareNow") {
    sharingMode = "shareNow";
  } else if (mode === "customScheduled" || mode === "schedule") {
    sharingMode = dueAt ? "customScheduled" : "addToQueue";
  }

  const mutation = `
    mutation CreatePost($input: CreatePostInput!) {
      createPost(input: $input) {
        ... on PostActionSuccess {
          post {
            id
            text
            status
          }
        }
        ... on MutationError {
          message
        }
      }
    }
  `;

  const inputPayload: any = {
    channelId,
    schedulingType: "automatic",
    mode: sharingMode,
    text: postText,
    assets: assets.length > 0 ? assets : undefined,
  };

  if (sharingMode === "customScheduled" && dueAt) {
    inputPayload.dueAt = dueAt;
  }

  const bufferRes = await fetch("https://api.buffer.com", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${bufferApiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      query: mutation,
      variables: { input: inputPayload },
    }),
  });

  const bufferData = await bufferRes.json();
  const result = bufferData.data?.createPost;

  if (bufferData.errors && bufferData.errors.length > 0) {
    const errMessage = bufferData.errors.map((e: any) => e.message).join(", ");
    if (uploadRequestId) {
      const supabase = getServiceSupabase();
      await supabase
        .from("upload_requests")
        .update({ error: `Buffer error: ${errMessage}`, updated_at: new Date().toISOString() })
        .eq("id", uploadRequestId);
    }
    throw new Error(`Buffer GraphQL error: ${errMessage}`);
  }

  if (result?.message) {
    // MutationError occurred
    if (uploadRequestId) {
      const supabase = getServiceSupabase();
      await supabase
        .from("upload_requests")
        .update({ error: `Buffer MutationError: ${result.message}`, updated_at: new Date().toISOString() })
        .eq("id", uploadRequestId);
    }
    throw new Error(`Buffer MutationError: ${result.message}`);
  }

  const createdPostId = result?.post?.id ?? null;

  if (uploadRequestId && createdPostId) {
    const supabase = getServiceSupabase();
    await supabase
      .from("upload_requests")
      .update({
        buffer_post_id: createdPostId,
        share_to_social: true,
        buffer_channel_id: channelId,
        updated_at: new Date().toISOString(),
      })
      .eq("id", uploadRequestId);
  }

  return {
    success: true,
    buffer_post_id: createdPostId,
    post_text: postText,
    was_trimmed: wasTrimmed,
    char_limit: charLimit,
  };
}
