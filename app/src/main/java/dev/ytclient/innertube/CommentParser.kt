package dev.ytclient.innertube

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Разбор страницы комментариев (ответ `next` с continuation). Понимает два формата:
 * старый `commentThreadRenderer.comment.commentRenderer` и новый, где в потоке только `commentViewModel`,
 * а тексты лежат в `frameworkUpdates.entityBatchUpdate.mutations[].payload.commentEntityPayload`.
 * Формат живых ответов проверить не удалось, поэтому поля ищутся с запасными вариантами.
 */
internal object CommentParser {

    fun parse(root: JsonElement?): CommentsPage {
        val payloads = HashMap<String, JsonElement>()
        for (m in root.at("frameworkUpdates", "entityBatchUpdate", "mutations").array()) {
            val p = m.at("payload", "commentEntityPayload") ?: continue
            val key = p.at("key").string() ?: m.at("entityKey").string() ?: continue
            payloads[key] = p
        }

        val comments = ArrayList<Comment>()
        var token: String? = null
        var createCommentParams: String? = null

        fun findString(element: JsonElement?, key: String): String? {
            when (element) {
                is JsonObject -> {
                    element[key].string()?.let { return it }
                    element.values.forEach { findString(it, key)?.let { value -> return value } }
                }
                is JsonArray -> element.forEach { findString(it, key)?.let { value -> return value } }
                else -> Unit
            }
            return null
        }

        fun walk(element: JsonElement?) {
            when (element) {
                is JsonObject -> for ((key, value) in element) {
                    when (key) {
                        "commentThreadRenderer" -> thread(value, payloads)?.let { comments += it }
                        "continuationItemRenderer" -> FeedParser.findToken(value)?.let { token = it }
                        "commentSimpleboxRenderer" -> {
                            createCommentParams = findString(value, "createCommentParams")
                                ?: findString(value, "commentCreateParams")
                        }
                        else -> walk(value)
                    }
                }
                is JsonArray -> element.forEach { walk(it) }
                else -> Unit
            }
        }

        walk(root)
        return CommentsPage(comments.distinctBy { it.id }, token, createCommentParams)
    }

    private fun thread(t: JsonElement, payloads: Map<String, JsonElement>): Comment? {
        // Старый формат.
        t.at("comment", "commentRenderer")?.let { r ->
            val id = r.at("commentId").string() ?: return null
            val text = r.at("contentText").text() ?: return null
            return Comment(
                id = id,
                author = r.at("authorText").text().orEmpty().trim(),
                avatar = r.at("authorThumbnail", "thumbnails").array().lastOrNull().at("url").string().https(),
                text = text,
                published = r.at("publishedTimeText").text(),
                likes = (r.at("voteCount").text() ?: r.at("likeCount").string())?.takeIf { it != "0" },
                liked = r.at("isLiked").string()?.toBooleanStrictOrNull()
                    ?: r.at("likeButton", "toggleButtonRenderer", "isToggled").string()?.toBooleanStrictOrNull(),
            )
        }

        // Новый формат.
        val vm = t.at("commentViewModel", "commentViewModel") ?: t.at("commentViewModel") ?: return null
        val payload = vm.at("commentKey").string()?.let { payloads[it] } ?: return null
        val text = payload.at("properties", "content", "content").string() ?: return null
        return Comment(
            id = payload.at("properties", "commentId").string() ?: vm.at("commentId").string() ?: return null,
            author = payload.at("author", "displayName").string().orEmpty().trim(),
            avatar = payload.at("avatar", "image", "sources", 0, "url").string().https()
                ?: payload.at("author", "avatarThumbnailUrl").string().https(),
            text = text,
            published = payload.at("properties", "publishedTime").string(),
            likes = payload.at("toolbar", "likeCountNotliked").string()?.takeIf { it != "0" && it.isNotBlank() },
            liked = payload.at("toolbar", "likeState").string()?.equals("LIKED", ignoreCase = true)
                ?: payload.at("toolbar", "isLiked").string()?.toBooleanStrictOrNull(),
        )
    }

    private fun String?.https(): String? = this?.let { if (it.startsWith("//")) "https:$it" else it }
}
