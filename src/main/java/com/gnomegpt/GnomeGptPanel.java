package com.gnomegpt;

import com.gnomegpt.chat.ChatMessage;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GnomeGptPanel extends PluginPanel
{
    private final GnomeGptPlugin plugin;
    private final JPanel chatContainer;
    private final JScrollPane scrollPane;
    private final JTextField inputField;
    private final JButton sendButton;
    private final JLabel statusLabel;
    private final String PLACEHOLDER = "Ask GnomeGPT anything...";

    // Current streaming bubble state
    private JTextPane streamingPane;
    private JPanel streamingBubble;

    private static final Color USER_BG = new Color(0x2D, 0x50, 0x3C);
    private static final Color GNOME_BG = new Color(0x2B, 0x3A, 0x52);
    private static final Color ERROR_BG = new Color(0x52, 0x2B, 0x2B);
    private static final Color TEXT_COLOR = new Color(0xE0, 0xE0, 0xE0);
    private static final Color LINK_COLOR = new Color(0x7D, 0xC8, 0xFF);
    private static final Color DIM_COLOR = new Color(0x90, 0x90, 0x90);
    private static final Color BOLD_COLOR = new Color(0xFF, 0xFF, 0xFF);
    private static final Color HEADER_BG = new Color(0x1B, 0x1B, 0x2B);
    private static final Color ACCENT = new Color(0xA0, 0xD0, 0xFF);

    private static final Pattern URL_PATTERN = Pattern.compile(
        "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)"
    );
    private static final Pattern WIKI_LINK_PATTERN = Pattern.compile(
        "\\[\\[([^\\]]+)\\]\\]"
    );
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern BULLET_PATTERN = Pattern.compile("^\\s*[-•]\\s+", Pattern.MULTILINE);
    private static final Pattern NUMBERED_PATTERN = Pattern.compile("^\\s*(\\d+)\\.\\s+", Pattern.MULTILINE);

    public GnomeGptPanel(GnomeGptPlugin plugin)
    {
        super(false);
        this.plugin = plugin;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(HEADER_BG);
        headerPanel.setBorder(new EmptyBorder(6, 10, 6, 10));
        headerPanel.setPreferredSize(new Dimension(0, 32));

        JLabel titleLabel = new JLabel("\uD83E\uDDD2 GnomeGPT");
        titleLabel.setForeground(ACCENT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JButton clearButton = new JButton("Clear");
        clearButton.setFont(clearButton.getFont().deriveFont(10f));
        clearButton.setFocusPainted(false);
        clearButton.setMargin(new Insets(2, 6, 2, 6));
        clearButton.addActionListener(e -> plugin.clearChat());
        headerPanel.add(clearButton, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // Chat area
        chatContainer = new JPanel();
        chatContainer.setLayout(new BoxLayout(chatContainer, BoxLayout.Y_AXIS));
        chatContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        chatContainer.setBorder(new EmptyBorder(6, 6, 6, 6));

        scrollPane = new JScrollPane(chatContainer);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        // Bottom: status + input
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        statusLabel = new JLabel(" ");
        statusLabel.setForeground(DIM_COLOR);
        statusLabel.setFont(statusLabel.getFont().deriveFont(10f));
        statusLabel.setBorder(new EmptyBorder(2, 8, 2, 8));
        bottomPanel.add(statusLabel, BorderLayout.NORTH);

        JPanel inputPanel = new JPanel(new BorderLayout(4, 0));
        inputPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        inputPanel.setBorder(new EmptyBorder(6, 6, 6, 6));

        inputField = new JTextField();
        inputField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        inputField.setForeground(DIM_COLOR);
        inputField.setCaretColor(TEXT_COLOR);
        inputField.setText(PLACEHOLDER);
        inputField.setBorder(new CompoundBorder(
            new LineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1),
            new EmptyBorder(4, 6, 4, 6)
        ));
        inputField.setFont(new Font("SansSerif", Font.PLAIN, 12));

        // Placeholder behavior
        inputField.addFocusListener(new FocusAdapter()
        {
            @Override
            public void focusGained(FocusEvent e)
            {
                if (inputField.getText().equals(PLACEHOLDER))
                {
                    inputField.setText("");
                    inputField.setForeground(TEXT_COLOR);
                }
            }

            @Override
            public void focusLost(FocusEvent e)
            {
                if (inputField.getText().isEmpty())
                {
                    inputField.setText(PLACEHOLDER);
                    inputField.setForeground(DIM_COLOR);
                }
            }
        });

        inputField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "send");
        inputField.getActionMap().put("send", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e) { sendMessage(); }
        });

        sendButton = new JButton("Send");
        sendButton.setFocusPainted(false);
        sendButton.setMargin(new Insets(4, 8, 4, 8));
        sendButton.addActionListener(e -> sendMessage());

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        bottomPanel.add(inputPanel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);

        // Welcome
        addBubble("Your OSRS companion. Ask me anything, or type /help.\nSet your API key in plugin settings to get started.", GNOME_BG, "GnomeGPT");
    }

    private void sendMessage()
    {
        String text = inputField.getText().trim();
        if (!text.isEmpty() && !text.equals(PLACEHOLDER))
        {
            inputField.setText("");
            inputField.setForeground(TEXT_COLOR);
            plugin.sendMessage(text);
        }
    }

    public void addMessage(ChatMessage message)
    {
        SwingUtilities.invokeLater(() ->
        {
            switch (message.getRole())
            {
                case USER:
                    addBubble(message.getContent(), USER_BG, "You");
                    break;
                case ASSISTANT:
                    boolean isError = message.getContent().startsWith("Error:") ||
                                      message.getContent().startsWith("Something went wrong:");
                    addBubble(message.getContent(), isError ? ERROR_BG : GNOME_BG, "GnomeGPT");
                    break;
            }
            scrollToBottom();
        });
    }

    /**
     * Start a streaming response bubble. Tokens will be appended via appendStreamToken().
     */
    public void startStreamingBubble()
    {
        SwingUtilities.invokeLater(() ->
        {
            streamingBubble = createBubbleShell(GNOME_BG, "GnomeGPT");
            streamingPane = createTextPane();

            // Show typing indicator
            StyledDocument doc = streamingPane.getStyledDocument();
            SimpleAttributeSet dimAttrs = new SimpleAttributeSet();
            StyleConstants.setForeground(dimAttrs, DIM_COLOR);
            StyleConstants.setItalic(dimAttrs, true);
            StyleConstants.setFontSize(dimAttrs, 12);
            try { doc.insertString(0, "thinking...", dimAttrs); } catch (BadLocationException e) {}

            streamingBubble.add(streamingPane, BorderLayout.CENTER);
            chatContainer.add(streamingBubble);
            chatContainer.add(Box.createVerticalStrut(4));
            chatContainer.revalidate();
            scrollToBottom();
        });
    }

    /**
     * Append a token to the streaming bubble.
     */
    public void appendStreamToken(String token)
    {
        SwingUtilities.invokeLater(() ->
        {
            if (streamingPane == null) return;

            StyledDocument doc = streamingPane.getStyledDocument();

            // On first real token, clear the "thinking..." text
            try
            {
                String current = doc.getText(0, doc.getLength());
                if (current.equals("thinking..."))
                {
                    doc.remove(0, doc.getLength());
                }
            }
            catch (BadLocationException e) {}

            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setForeground(attrs, TEXT_COLOR);
            StyleConstants.setFontFamily(attrs, "SansSerif");
            StyleConstants.setFontSize(attrs, 12);

            try
            {
                doc.insertString(doc.getLength(), token, attrs);
            }
            catch (BadLocationException e) {}

            scrollToBottom();
        });
    }

    /**
     * Finalize the streaming bubble — re-render with full formatting (links, bold, etc).
     */
    public void finalizeStreamBubble(String fullText)
    {
        SwingUtilities.invokeLater(() ->
        {
            if (streamingBubble == null || streamingPane == null) return;

            // Replace the streaming pane with a properly formatted one
            streamingBubble.remove(streamingPane);
            JTextPane formatted = createTextPane();
            appendFormattedText(formatted, fullText);
            streamingBubble.add(formatted, BorderLayout.CENTER);

            streamingBubble.revalidate();
            streamingBubble.repaint();

            streamingPane = null;
            streamingBubble = null;
            scrollToBottom();
        });
    }

    public void setLoading(boolean loading)
    {
        SwingUtilities.invokeLater(() ->
        {
            inputField.setEnabled(!loading);
            sendButton.setEnabled(!loading);
            statusLabel.setText(loading ? "Thinking..." : " ");
        });
    }

    public void clearMessages()
    {
        SwingUtilities.invokeLater(() ->
        {
            chatContainer.removeAll();
            streamingPane = null;
            streamingBubble = null;
            addBubble("Chat cleared.", GNOME_BG, "GnomeGPT");
            chatContainer.revalidate();
            chatContainer.repaint();
        });
    }

    // --- Private helpers ---

    private void addBubble(String text, Color bgColor, String sender)
    {
        JPanel bubble = createBubbleShell(bgColor, sender);
        JTextPane textPane = createTextPane();
        appendFormattedText(textPane, text);
        bubble.add(textPane, BorderLayout.CENTER);

        chatContainer.add(bubble);
        chatContainer.add(Box.createVerticalStrut(4));
        chatContainer.revalidate();
    }

    private JPanel createBubbleShell(Color bgColor, String sender)
    {
        JPanel bubble = new JPanel(new BorderLayout());
        bubble.setBackground(bgColor);
        bubble.setBorder(new CompoundBorder(
            new EmptyBorder(2, 0, 2, 0),
            new CompoundBorder(
                new LineBorder(bgColor.darker(), 1, true),
                new EmptyBorder(6, 8, 6, 8)
            )
        ));

        JLabel senderLabel = new JLabel(sender);
        senderLabel.setForeground(sender.equals("You")
            ? new Color(0x60, 0xD0, 0x80) : ACCENT);
        senderLabel.setFont(senderLabel.getFont().deriveFont(Font.BOLD, 11f));
        senderLabel.setBorder(new EmptyBorder(0, 0, 3, 0));
        bubble.add(senderLabel, BorderLayout.NORTH);

        bubble.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 12, Integer.MAX_VALUE));
        bubble.setAlignmentX(Component.LEFT_ALIGNMENT);

        return bubble;
    }

    private JTextPane createTextPane()
    {
        JTextPane pane = new JTextPane();
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setFont(new Font("SansSerif", Font.PLAIN, 12));
        pane.setBorder(null);
        pane.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 30, Integer.MAX_VALUE));
        return pane;
    }

    private void appendFormattedText(JTextPane pane, String text)
    {
        StyledDocument doc = pane.getStyledDocument();

        // 1. Convert [[wiki terms]] to clickable text
        // 2. Handle **bold**
        // 3. Handle bullet points
        // 4. Handle URLs

        // First pass: convert wiki links and track positions
        text = processWikiLinks(text);

        // Split into segments by **bold** markers
        List<TextSegment> segments = parseMarkdown(text);

        for (TextSegment seg : segments)
        {
            if (seg.isLink)
            {
                appendLinkSegment(doc, seg.text, seg.url, pane);
            }
            else
            {
                SimpleAttributeSet attrs = new SimpleAttributeSet();
                StyleConstants.setFontFamily(attrs, "SansSerif");
                StyleConstants.setFontSize(attrs, 12);

                if (seg.isBold)
                {
                    StyleConstants.setBold(attrs, true);
                    StyleConstants.setForeground(attrs, BOLD_COLOR);
                }
                else if (seg.isBullet)
                {
                    StyleConstants.setForeground(attrs, new Color(0xFF, 0xD7, 0x00));
                }
                else
                {
                    StyleConstants.setForeground(attrs, TEXT_COLOR);
                }

                try { doc.insertString(doc.getLength(), seg.text, attrs); }
                catch (BadLocationException e) {}
            }
        }

        // Add link click handler
        addLinkHandlers(pane, doc);
    }

    /**
     * Process [[wiki links]] into plain text + track for later linking.
     * Returns text with wiki terms as plain text (linking done in segment parsing).
     */
    private String processWikiLinks(String text)
    {
        // We'll handle wiki links in the segment parser
        return text;
    }

    private List<TextSegment> parseMarkdown(String text)
    {
        List<TextSegment> segments = new ArrayList<>();

        // Process the text character by character, handling patterns
        // First, find all wiki links, URLs, and bold regions

        // Step 1: Replace bullet points with bullet character
        text = BULLET_PATTERN.matcher(text).replaceAll("  • ");

        // Step 2: Parse into segments
        int pos = 0;
        while (pos < text.length())
        {
            // Check for wiki link [[term]]
            if (text.startsWith("[[", pos))
            {
                int end = text.indexOf("]]", pos + 2);
                if (end >= 0)
                {
                    String term = text.substring(pos + 2, end);
                    String url = "https://oldschool.runescape.wiki/w/" + term.replace(" ", "_");
                    segments.add(new TextSegment(term, true, url, false, false));
                    pos = end + 2;
                    continue;
                }
            }

            // Check for bold **text**
            if (text.startsWith("**", pos))
            {
                int end = text.indexOf("**", pos + 2);
                if (end >= 0)
                {
                    String bold = text.substring(pos + 2, end);
                    segments.add(new TextSegment(bold, false, null, true, false));
                    pos = end + 2;
                    continue;
                }
            }

            // Check for URL
            Matcher urlMatcher = URL_PATTERN.matcher(text);
            if (urlMatcher.find(pos) && urlMatcher.start() == pos)
            {
                String url = urlMatcher.group(1);
                segments.add(new TextSegment(url, true, url, false, false));
                pos = urlMatcher.end();
                continue;
            }

            // Find next special marker
            int nextWiki = text.indexOf("[[", pos);
            int nextBold = text.indexOf("**", pos);
            int nextUrl = Integer.MAX_VALUE;
            Matcher um = URL_PATTERN.matcher(text);
            if (um.find(pos)) nextUrl = um.start();

            int nextSpecial = Math.min(
                nextWiki >= 0 ? nextWiki : Integer.MAX_VALUE,
                Math.min(
                    nextBold >= 0 ? nextBold : Integer.MAX_VALUE,
                    nextUrl
                )
            );

            if (nextSpecial == Integer.MAX_VALUE) nextSpecial = text.length();

            // Plain text up to next marker
            if (nextSpecial > pos)
            {
                String plain = text.substring(pos, nextSpecial);
                // Check for bullet points in this plain segment
                if (plain.contains("• "))
                {
                    String[] parts = plain.split("(• )", -1);
                    for (int i = 0; i < parts.length; i++)
                    {
                        if (!parts[i].isEmpty())
                        {
                            segments.add(new TextSegment(parts[i], false, null, false, false));
                        }
                        if (i < parts.length - 1)
                        {
                            segments.add(new TextSegment("• ", false, null, false, true));
                        }
                    }
                }
                else
                {
                    segments.add(new TextSegment(plain, false, null, false, false));
                }
                pos = nextSpecial;
            }
        }

        return segments;
    }

    private void appendLinkSegment(StyledDocument doc, String text, String url, JTextPane pane)
    {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setForeground(attrs, LINK_COLOR);
        StyleConstants.setUnderline(attrs, true);
        StyleConstants.setFontFamily(attrs, "SansSerif");
        StyleConstants.setFontSize(attrs, 12);
        attrs.addAttribute("url", url);

        try { doc.insertString(doc.getLength(), text, attrs); }
        catch (BadLocationException e) {}
    }

    private boolean linkHandlerAdded = false;

    private void addLinkHandlers(JTextPane pane, StyledDocument doc)
    {
        pane.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                int clickPos = pane.viewToModel2D(e.getPoint());
                if (clickPos >= 0)
                {
                    AttributeSet attrs = doc.getCharacterElement(clickPos).getAttributes();
                    Object url = attrs.getAttribute("url");
                    if (url != null)
                    {
                        try { Desktop.getDesktop().browse(new URI(url.toString())); }
                        catch (Exception ex) {}
                    }
                }
            }
        });

        pane.addMouseMotionListener(new java.awt.event.MouseMotionAdapter()
        {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e)
            {
                int hoverPos = pane.viewToModel2D(e.getPoint());
                if (hoverPos >= 0)
                {
                    AttributeSet attrs = doc.getCharacterElement(hoverPos).getAttributes();
                    pane.setCursor(attrs.getAttribute("url") != null
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
                }
            }
        });
    }

    private void scrollToBottom()
    {
        SwingUtilities.invokeLater(() ->
        {
            JScrollBar sb = scrollPane.getVerticalScrollBar();
            sb.setValue(sb.getMaximum());
        });
    }

    static class TextSegment
    {
        final String text;
        final boolean isLink;
        final String url;
        final boolean isBold;
        final boolean isBullet;

        TextSegment(String text, boolean isLink, String url, boolean isBold, boolean isBullet)
        {
            this.text = text;
            this.isLink = isLink;
            this.url = url;
            this.isBold = isBold;
            this.isBullet = isBullet;
        }
    }
}
