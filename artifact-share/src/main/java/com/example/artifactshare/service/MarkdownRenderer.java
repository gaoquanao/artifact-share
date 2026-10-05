package com.example.artifactshare.service;

import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.Text;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.parser.ParserEmulationProfile;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.springframework.stereotype.Component;

/**
 * Markdown 转 HTML(GFM 语法:表格、删除线、任务列表等),产出带基础样式的完整页面。
 */
@Component
public class MarkdownRenderer {

    private static final MutableDataSet OPTIONS = new MutableDataSet()
            .setFrom(ParserEmulationProfile.GITHUB);
    private static final Parser PARSER = Parser.builder(OPTIONS).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder(OPTIONS).build();

    private static final String PAGE_TEMPLATE = """
            <!doctype html>
            <html lang="zh-CN">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>%s</title>
            <style>
              body { margin: 0 auto; max-width: 860px; padding: 24px 20px 64px;
                     font-family: -apple-system, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
                     line-height: 1.7; color: #24292f; }
              h1, h2, h3 { line-height: 1.3; margin-top: 1.6em; }
              pre { background: #f6f8fa; padding: 12px; border-radius: 8px; overflow: auto; }
              code { font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: .9em; }
              img { max-width: 100%%; }
              table { border-collapse: collapse; }
              th, td { border: 1px solid #d0d7de; padding: 6px 12px; }
              blockquote { color: #57606a; border-left: 4px solid #d0d7de; margin: 0; padding: 0 1em; }
            </style>
            </head>
            <body>
            %s
            </body>
            </html>
            """;

    public String render(String markdown) {
        Document document = PARSER.parse(markdown);
        String body = RENDERER.render(document);
        return PAGE_TEMPLATE.formatted(escapeHtml(firstHeading(document)), body);
    }

    private String firstHeading(Document document) {
        for (Node node : document.getChildren()) {
            if (node instanceof Heading heading) {
                StringBuilder sb = new StringBuilder();
                collectText(heading, sb);
                String text = sb.toString().trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return "文档";
    }

    private void collectText(Node node, StringBuilder sb) {
        for (Node child : node.getChildren()) {
            if (child instanceof Text text) {
                sb.append(text.getChars());
            } else {
                collectText(child, sb);
            }
        }
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
