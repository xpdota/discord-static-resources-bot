package gg.xp.resbot.markdown

import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor
import org.commonmark.node.*
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.markdown.MarkdownNodeRendererContext
import org.commonmark.renderer.markdown.MarkdownWriter
import org.commonmark.text.AsciiMatcher
import org.commonmark.text.CharMatcher

import java.util.regex.Pattern

@CompileStatic
@TupleConstructor(includeFields = true, defaults = false)
class DiscordMarkdownNodeRenderer implements NodeRenderer {

	private static final CharMatcher linkDestinationEscapeInAngleBrackets = AsciiMatcher.builder().with {
		c '<' as char
		c '>' as char
		c '\n' as char
		c '\\' as char
		build()
	}
	private static final CharMatcher linkTitleEscapeInQuotes = AsciiMatcher.builder().with {
		c '"' as char
		c '\n' as char
		c '\\' as char
		build()
	}
	private final AsciiMatcher textEscape = AsciiMatcher.builder().anyOf("[]`*_\n\\").anyOf(context.getSpecialCharacters()).build()
	private final AsciiMatcher textEscapeInHeading = AsciiMatcher.builder(textEscape).anyOf("#").build()

	private static final Pattern urlRegex = ~/https?:\/\/[^\s$]+/

	private final MarkdownNodeRendererContext context;

	@Override
	Set<Class<? extends Node>> getNodeTypes() {
		return [Link, Text, Emphasis, StrongEmphasis] as Set<Class<? extends Node>>
	}

	@Override
	void render(Node node) {
		if (node instanceof Link) {
			MarkdownWriter writer = context.writer

			writer.with {
				raw '['
				visitChildren node
				raw ']'
				raw '('
				raw '<'
				text node.destination, linkDestinationEscapeInAngleBrackets
				raw '>'

				String title = node.title
				if (title != null) {
					raw ' '
					raw '"'
					text title, linkTitleEscapeInQuotes
					raw '"'
				}
				raw ')'
			}
		}
		else if (node instanceof Text) {
			context.writer.with {
				if (node.parent instanceof Link) {
					text node.literal, linkTitleEscapeInQuotes
				}
				else if (node.parent instanceof Heading) {
					text node.literal, textEscapeInHeading
				}
				else {
					// Write URLs in raw form, don't escape.
					// Discord won't recognize the escapes. You will end up with garbage URLs.
					def matcher = urlRegex.matcher node.literal
					int currentIndex = 0
					while (matcher.find()) {
						int start = matcher.start()
						int end = matcher.end()
						text node.literal.substring(currentIndex, start), textEscape
						raw matcher.group(0)
						currentIndex = end
					}
					text node.literal.substring(currentIndex), textEscape
				}
			}
		}
		else if (node instanceof Emphasis || node instanceof StrongEmphasis) {
			String delimiter = node.openingDelimiter
			// Use delimiter that was parsed if available
			if (delimiter == null) {
				// When emphasis is nested, a different delimiter needs to be used
				delimiter = context.writer.lastChar == ('*' as char) ? "_" : "*"
			}
			context.writer.raw delimiter
			visitChildren node
			context.writer.raw delimiter
		}
	}

	private void visitChildren(Node parent) {
		Node next
		for (Node node = parent.getFirstChild(); node != null; node = next) {
			next = node.getNext();
			this.context.render(node);
		}
	}
}
