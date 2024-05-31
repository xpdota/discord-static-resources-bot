package gg.xp

import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.markdown.MarkdownNodeRendererContext
import org.commonmark.renderer.markdown.MarkdownNodeRendererFactory
import org.commonmark.renderer.markdown.MarkdownRenderer

@CompileStatic
@TupleConstructor(includeFields = true, defaults = false)
class DesiredMarkdownMessage {

	private final Bot bot
	private final File file

	static DesiredMarkdownMessage fromFile(Bot bot, File file) {
		if (file.isFile()) {
			return new DesiredMarkdownMessage(bot, file)
		}
		else {
			throw new IllegalArgumentException("File '${file}' does not exist or is not a normal file")
		}
	}

	DesiredMessageContent getDesiredContent() {
		def parser = Parser.builder().build()
		def renderer = MarkdownRenderer.builder().with {
			nodeRendererFactory(new DiscordMarkdownNodeRendererFactory())
			build()
		}
		Node document = parser.parse(file.text)
		boolean pending = false
		document.accept(new AbstractVisitor() {
			@Override
			void visit(Link link) {
				def resolved = bot.resolveLink(file, link.destination)
				if (resolved.pending()) {
					pending = true
				}
				// TODO: Prevent embeds
				// This is currently broken because CoreMarkdownNodeRenderer has a list of special characters that
				// justify wrapping the url in <>, which is a MD way of working around having a literal ) in the URL.
				link.destination = "${resolved.value()}"
				// doesn't work
//				link.insertBefore(new Text("<"))
//				link.insertAfter(new Text(">"))
			}
		})

		def rendered = renderer.render(document).stripTrailing()
		return new DesiredMessageContent(rendered, pending)
	}

	File getFile() {
		return file
	}
}