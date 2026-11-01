package com.kori.korichat.html

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

private const val LINK_URL_TAG = "URL"

@Composable
fun HtmlContent(html: String) {
    val doc = Jsoup.parse(html)
    val body = doc.body()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RenderHtmlElement(body)
    }
}

@Composable
fun RenderHtmlElement(element: Element) {
    for (node in element.childNodes()) {
        when (node) {
            is Element -> {
                when (node.tagName()) {
                    "h1", "h2", "h3" -> {
                        Text(
                            text = node.text(),
                            fontSize = if (node.tagName() == "h2") 20.sp else 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    "p" -> {
                        HtmlParagraph(node)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    "a" -> {
                        HtmlParagraph(node)
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    "img" -> {
                        HtmlImage(node.attr("src"))
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    "table" -> {
                        HtmlTable(node)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    "br" -> {
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    "ul", "ol" -> {
                        HtmlList(node, ordered = node.tagName() == "ol")
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    else -> {
                        RenderHtmlElement(node) // recursive
                    }
                }
            }

            is TextNode -> {
                if (node.text().isNotBlank()) {
                    Text(text = node.text())
                }
            }
        }
    }
}

@Composable
private fun HtmlParagraph(element: Element) {
    val annotated = buildAnnotatedFromChildren(element)
    val uriHandler = LocalUriHandler.current
    ClickableText(
        text = annotated,
        style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Color.Black),
        onClick = { offset ->
            annotated.getStringAnnotations(LINK_URL_TAG, offset, offset)
                .firstOrNull()
                ?.let { uriHandler.openUri(it.item) }
        }
    )
}

@Composable
private fun HtmlImage(src: String) {
    if (src.isBlank()) return

    if (src.startsWith("data:")) {
        val base64 = src.substringAfter(",", "")
        val bitmap = remember(src) {
            runCatching {
                val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
        } else {
            BrokenImagePlaceholder()
        }
    } else {
        var failed by remember(src) { mutableStateOf(false) }
        if (failed) {
            BrokenImagePlaceholder()
        } else {
            AsyncImage(
                model = src,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
                onError = { failed = true }
            )
        }
    }
}

@Composable
private fun BrokenImagePlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.BrokenImage,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = Color(0xFFB0B4BB)
        )
    }
}

@Composable
private fun HtmlList(list: Element, ordered: Boolean) {
    val uriHandler = LocalUriHandler.current
    Column {
        list.children().filter { it.tagName() == "li" }.forEachIndexed { index, li ->
            val marker = if (ordered) "${index + 1}." else "•"
            val annotated = buildAnnotatedFromChildren(li)
            Row(modifier = Modifier.fillMaxWidth().padding(start = 8.dp)) {
                Text(text = marker, fontSize = 14.sp, modifier = Modifier.padding(end = 6.dp))
                ClickableText(
                    text = annotated,
                    style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Color.Black),
                    onClick = { offset ->
                        annotated.getStringAnnotations(LINK_URL_TAG, offset, offset)
                            .firstOrNull()
                            ?.let { uriHandler.openUri(it.item) }
                    }
                )
            }
        }
    }
}

@Composable
private fun HtmlTable(table: Element) {
    val rows = table.select("tr")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column {
            rows.forEach { row ->
                val cells = row.children().filter { it.tagName() == "td" || it.tagName() == "th" }
                Row(modifier = Modifier.fillMaxWidth()) {
                    cells.forEach { cell ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(0.5.dp, Color(0xFFEAECF0))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = buildAnnotatedFromChildren(cell),
                                fontSize = 13.sp,
                                fontWeight = if (cell.tagName() == "th") FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Builds inline styled text for `<p>`/`<a>`/other inline containers. Links carry a
 * [LINK_URL_TAG] string annotation so [HtmlParagraph] can resolve taps back to a URL.
 */
fun buildAnnotatedFromChildren(element: Element): AnnotatedString {
    return buildAnnotatedString {
        for (child in element.childNodes()) {
            when (child) {
                is TextNode -> append(child.text())
                is Element -> {
                    when (child.tagName()) {
                        "b", "strong" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(buildAnnotatedFromChildren(child))
                        }

                        "i", "em" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(buildAnnotatedFromChildren(child))
                        }

                        "u" -> withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                            append(buildAnnotatedFromChildren(child))
                        }

                        "a" -> {
                            val start = length
                            withStyle(
                                SpanStyle(
                                    color = Color(0xFF1A73E8),
                                    textDecoration = TextDecoration.Underline
                                )
                            ) {
                                append(buildAnnotatedFromChildren(child))
                            }
                            addStringAnnotation(LINK_URL_TAG, child.attr("href"), start, length)
                        }

                        "br" -> append("\n")

                        "ul", "ol" -> {
                            val items = child.children().filter { it.tagName() == "li" }
                            items.forEachIndexed { index, li ->
                                val marker = if (child.tagName() == "ol") "${index + 1}." else "•"
                                append("$marker ")
                                append(buildAnnotatedFromChildren(li))
                                if (index != items.lastIndex) append("\n")
                            }
                        }

                        else -> append(buildAnnotatedFromChildren(child))
                    }
                }
            }
        }
    }
}
