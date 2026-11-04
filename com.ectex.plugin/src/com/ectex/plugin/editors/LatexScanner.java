package com.ectex.plugin.editors;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.EndOfLineRule;
import org.eclipse.jface.text.rules.ICharacterScanner;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;

/**
 * Highlights comments, commands, structure commands, math and brackets.
 * All rules are single-line so the default damager/repairer stays exact.
 */
public class LatexScanner extends RuleBasedScanner {

	private static final Set<String> STRUCTURE = Set.of("documentclass", "usepackage", "begin", "end", "part",
			"chapter", "section", "subsection", "subsubsection", "paragraph", "subparagraph", "input", "include",
			"label", "ref", "cite", "newcommand", "renewcommand", "def");

	public LatexScanner() {
		IToken comment = token("ectex.comment", new RGB(63, 127, 95), SWT.ITALIC);
		IToken command = token("ectex.command", new RGB(46, 109, 180), SWT.NONE);
		IToken structure = token("ectex.structure", new RGB(163, 61, 143), SWT.BOLD);
		IToken math = token("ectex.math", new RGB(196, 98, 16), SWT.NONE);
		IToken bracket = token("ectex.bracket", new RGB(128, 128, 128), SWT.NONE);

		List<IRule> rules = new ArrayList<>();
		// Commands come first so "\%" and "\$" are consumed before comments and math can see them.
		rules.add(new CommandRule(command, structure));
		rules.add(new EndOfLineRule("%", comment));
		rules.add(new SingleLineRule("$$", "$$", math, '\\'));
		rules.add(new SingleLineRule("$", "$", math, '\\'));
		rules.add(scanner -> {
			int c = scanner.read();
			if (c == '{' || c == '}' || c == '[' || c == ']') {
				return bracket;
			}
			scanner.unread();
			return Token.UNDEFINED;
		});
		setRules(rules.toArray(new IRule[0]));
	}

	private static IToken token(String key, RGB rgb, int style) {
		ColorRegistry registry = JFaceResources.getColorRegistry();
		if (!registry.hasValueFor(key)) {
			registry.put(key, rgb);
		}
		Color color = registry.get(key);
		return new Token(new TextAttribute(color, null, style));
	}

	/** Matches a backslash followed by a control word ({@code \section*}) or a control symbol ({@code \%}). */
	private static class CommandRule implements IRule {

		private final IToken command;
		private final IToken structure;

		CommandRule(IToken command, IToken structure) {
			this.command = command;
			this.structure = structure;
		}

		@Override
		public IToken evaluate(ICharacterScanner scanner) {
			if (scanner.read() != '\\') {
				scanner.unread();
				return Token.UNDEFINED;
			}
			int c = scanner.read();
			if (!isLetter(c)) {
				if (c == ICharacterScanner.EOF) {
					scanner.unread();
				}
				return command; // "\\", "\%", "\{", "\[" ...
			}
			StringBuilder name = new StringBuilder();
			while (isLetter(c)) {
				name.append((char) c);
				c = scanner.read();
			}
			if (c != '*') {
				scanner.unread();
			}
			return STRUCTURE.contains(name.toString()) ? structure : command;
		}

		private static boolean isLetter(int c) {
			return c != ICharacterScanner.EOF && (Character.isLetter(c) || c == '@');
		}
	}
}
