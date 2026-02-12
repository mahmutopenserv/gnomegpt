package com.gnomegpt;

import com.gnomegpt.chat.ChatMessage;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
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

/**
 * RuneScape-themed chat panel for GnomeGPT.
 */
public class GnomeGptPanel extends PluginPanel
{
    private final GnomeGptPlugin plugin;
    private final JPanel chatContainer;
    private final JScrollPane scrollPane;
    private final JTextField inputField;
    private final JButton sendButton;
    private final JLabel statusLabel;
    private final String PLACEHOLDER = "Ask GnomeGPT anything...";

    // Streaming state
    private JTextPane streamingPane;
    private JPanel streamingBubble;

    // === RuneScape Color Palette ===
    // Chat background: dark brown stone
    private static final Color RS_BG_DARK = new Color(0x30, 0x28, 0x1C);
    private static final Color RS_BG_MID = new Color(0x3E, 0x35, 0x29);
    private static final Color RS_BG_LIGHT = new Color(0x4A, 0x40, 0x30);

    // Chat text colors (from RS chatbox)
    private static final Color RS_YELLOW = new Color(0xFF, 0xFF, 0x00);        // Standard chat
    private static final Color RS_CYAN = new Color(0x00, 0xFF, 0xFF);          // Player name
    private static final Color RS_GREEN = new Color(0x00, 0xFF, 0x00);         // Tradeable items
    private static final Color RS_BLUE = new Color(0x00, 0x80, 0xFF);          // Links
    private static final Color RS_WHITE = new Color(0xFF, 0xFF, 0xFF);         // Bold/headers
    private static final Color RS_ORANGE = new Color(0xFF, 0x98, 0x10);        // NPC dialogue
    private static final Color RS_RED = new Color(0xFF, 0x30, 0x30);           // Errors
    private static final Color RS_GREY = new Color(0x9F, 0x96, 0x87);          // Dim text
    private static final Color RS_BORDER = new Color(0x5C, 0x50, 0x3C);        // Border color
    private static final Color RS_BORDER_DARK = new Color(0x25, 0x20, 0x18);   // Outer border

    private static final Pattern URL_PATTERN = Pattern.compile(
        "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)"
    );
    private static final Pattern WIKI_LINK_PATTERN = Pattern.compile(
        "\\[\\[([^\\]]+)\\]\\]"
    );
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");

    public GnomeGptPanel(GnomeGptPlugin plugin)
    {
        super(false);
        this.plugin = plugin;

        setLayout(new BorderLayout());
        setBackground(RS_BG_DARK);

        // === Header (styled like RS interface title) ===
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(RS_BG_MID);
        headerPanel.setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 2, 0, RS_BORDER),
            new EmptyBorder(5, 8, 5, 8)
        ));
        headerPanel.setPreferredSize(new Dimension(0, 30));

        JLabel titleLabel = new JLabel("GnomeGPT");
        titleLabel.setForeground(RS_ORANGE);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JButton clearButton = new JButton("Clear");
        clearButton.setFont(new Font("SansSerif", Font.PLAIN, 10));
        clearButton.setFocusPainted(false);
        clearButton.setBackground(RS_BG_LIGHT);
        clearButton.setForeground(RS_YELLOW);
        clearButton.setBorder(new CompoundBorder(
            new LineBorder(RS_BORDER, 1),
            new EmptyBorder(2, 6, 2, 6)
        ));
        clearButton.setMargin(new Insets(1, 4, 1, 4));
        clearButton.addActionListener(e -> plugin.clearChat());
        headerPanel.add(clearButton, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // === Chat area (RS chatbox style) ===
        chatContainer = new JPanel();
        chatContainer.setLayout(new BoxLayout(chatContainer, BoxLayout.Y_AXIS));
        chatContainer.setBackground(RS_BG_DARK);
        chatContainer.setBorder(new EmptyBorder(4, 4, 4, 4));

        scrollPane = new JScrollPane(chatContainer);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(new LineBorder(RS_BORDER_DARK, 1));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(RS_BG_DARK);
        add(scrollPane, BorderLayout.CENTER);

        // === Bottom: status + input (RS style input box) ===
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(RS_BG_DARK);

        statusLabel = new JLabel(" ");
        statusLabel.setForeground(RS_GREY);
        statusLabel.setFont(new Font("SansSerif", Font.ITALIC, 10));
        statusLabel.setBorder(new EmptyBorder(2, 6, 2, 6));
        bottomPanel.add(statusLabel, BorderLayout.NORTH);

        JPanel inputPanel = new JPanel(new BorderLayout(3, 0));
        inputPanel.setBackground(RS_BG_MID);
        inputPanel.setBorder(new CompoundBorder(
            new MatteBorder(2, 0, 0, 0, RS_BORDER),
            new EmptyBorder(5, 5, 5, 5)
        ));

        inputField = new JTextField();
        inputField.setBackground(RS_BG_DARK);
        inputField.setForeground(RS_GREY);
        inputField.setCaretColor(RS_YELLOW);
        inputField.setText(PLACEHOLDER);
        inputField.setBorder(new CompoundBorder(
            new LineBorder(RS_BORDER, 1),
            new EmptyBorder(3, 5, 3, 5)
        ));
        inputField.setFont(new Font("SansSerif", Font.PLAIN, 12));

        inputField.addFocusListener(new FocusAdapter()
        {
            @Override
            public void focusGained(FocusEvent e)
            {
                if (inputField.getText().equals(PLACEHOLDER))
                {
                    inputField.setText("");
                    inputField.setForeground(RS_YELLOW);
                }
            }

            @Override
            public void focusLost(FocusEvent e)
            {
                if (inputField.getText().isEmpty())
                {
                    inputField.setText(PLACEHOLDER);
                    inputField.setForeground(RS_GREY);
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
        sendButton.setBackground(RS_BG_LIGHT);
        sendButton.setForeground(RS_YELLOW);
        sendButton.setBorder(new CompoundBorder(
            new LineBorder(RS_BORDER, 1),
            new EmptyBorder(3, 8, 3, 8)
        ));
        sendButton.setFont(new Font("SansSerif", Font.BOLD, 11));
        sendButton.addActionListener(e -> sendMessage());

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        bottomPanel.add(inputPanel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);

        // Welcome message
        addWelcome();
    }

    private void addWelcome()
    {
        addChatLine("GnomeGPT", RS_ORANGE, "Ask me anything about OSRS. Type /help for commands.", RS_YELLOW);
    }

    private void sendMessage()
    {
        String text = inputField.getText().trim();
        if (!text.isEmpty() && !text.equals(PLACEHOLDER))
        {
            inputField.setText("");
            inputField.setForeground(RS_YELLOW);
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
                    addChatLine("You", RS_CYAN, message.getContent(), RS_YELLOW);
                    break;
                case ASSISTANT:
                    boolean isError = message.getContent().startsWith("Error:") ||
                                      message.getContent().startsWith("Something went wrong:");
                    addFormattedChatLine("GnomeGPT", RS_ORANGE,
                        message.getContent(), isError ? RS_RED : RS_YELLOW);
                    break;
            }
            scrollToBottom();
        });
    }

    // === Streaming ===

    public void startStreamingBubble()
    {
        SwingUtilities.invokeLater(() ->
        {
            streamingBubble = createBubble(false);

            // Name label
            JLabel nameLabel = new JLabel("GnomeGPT: ");
            nameLabel.setForeground(RS_ORANGE);
            nameLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
            streamingBubble.add(nameLabel, BorderLayout.NORTH);

            streamingPane = createTextPane();
            StyledDocument doc = streamingPane.getStyledDocument();
            SimpleAttributeSet dimAttrs = new SimpleAttributeSet();
            StyleConstants.setForeground(dimAttrs, RS_GREY);
            StyleConstants.setItalic(dimAttrs, true);
            StyleConstants.setFontSize(dimAttrs, 12);
            try { doc.insertString(0, "thinking...", dimAttrs); } catch (BadLocationException e) {}

            streamingBubble.add(streamingPane, BorderLayout.CENTER);
            chatContainer.add(streamingBubble);
            chatContainer.add(Box.createVerticalStrut(2));
            chatContainer.revalidate();
            scrollToBottom();
        });
    }

    public void appendStreamToken(String token)
    {
        SwingUtilities.invokeLater(() ->
        {
            if (streamingPane == null) return;
            StyledDocument doc = streamingPane.getStyledDocument();

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
            StyleConstants.setForeground(attrs, RS_YELLOW);
            StyleConstants.setFontFamily(attrs, "SansSerif");
            StyleConstants.setFontSize(attrs, 12);

            try { doc.insertString(doc.getLength(), token, attrs); }
            catch (BadLocationException e) {}

            scrollToBottom();
        });
    }

    public void finalizeStreamBubble(String fullText)
    {
        SwingUtilities.invokeLater(() ->
        {
            if (streamingBubble == null || streamingPane == null) return;

            streamingBubble.remove(streamingPane);
            JTextPane formatted = createTextPane();
            appendFormattedText(formatted, fullText, RS_YELLOW);
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
            addChatLine("GnomeGPT", RS_ORANGE, "Chat cleared.", RS_GREY);
            chatContainer.revalidate();
            chatContainer.repaint();
        });
    }

    // === Private helpers ===

    // Colors for user vs gnome bubbles
    private static final Color USER_BUBBLE_BG = new Color(0x38, 0x30, 0x22);
    private static final Color GNOME_BUBBLE_BG = new Color(0x2C, 0x28, 0x1E);
    private static final Color USER_BUBBLE_BORDER = new Color(0x50, 0x48, 0x38);
    private static final Color GNOME_BUBBLE_BORDER = new Color(0x48, 0x40, 0x30);
    // Highlight strip on left edge
    private static final Color USER_ACCENT = new Color(0x00, 0xCC, 0xCC);
    private static final Color GNOME_ACCENT = new Color(0xCC, 0x88, 0x00);

    /**
     * Add a simple chat line in a stone-tablet bubble.
     */
    private void addChatLine(String sender, Color nameColor, String text, Color textColor)
    {
        boolean isUser = sender.equals("You");
        JPanel bubble = createBubble(isUser);

        JTextPane pane = createTextPane();
        StyledDocument doc = pane.getStyledDocument();

        SimpleAttributeSet nameAttrs = new SimpleAttributeSet();
        StyleConstants.setForeground(nameAttrs, nameColor);
        StyleConstants.setBold(nameAttrs, true);
        StyleConstants.setFontFamily(nameAttrs, "SansSerif");
        StyleConstants.setFontSize(nameAttrs, 12);

        SimpleAttributeSet textAttrs = new SimpleAttributeSet();
        StyleConstants.setForeground(textAttrs, textColor);
        StyleConstants.setFontFamily(textAttrs, "SansSerif");
        StyleConstants.setFontSize(textAttrs, 12);

        try
        {
            doc.insertString(doc.getLength(), sender + ": ", nameAttrs);
            doc.insertString(doc.getLength(), text, textAttrs);
        }
        catch (BadLocationException e) {}

        addLinkHandlers(pane, doc);
        bubble.add(pane, BorderLayout.CENTER);

        chatContainer.add(bubble);
        chatContainer.add(Box.createVerticalStrut(4));
        chatContainer.revalidate();
    }

    /**
     * Add a formatted chat line with wiki links, bold, etc.
     */
    private void addFormattedChatLine(String sender, Color nameColor, String text, Color textColor)
    {
        boolean isUser = sender.equals("You");
        JPanel bubble = createBubble(isUser);

        JTextPane pane = createTextPane();
        StyledDocument doc = pane.getStyledDocument();

        SimpleAttributeSet nameAttrs = new SimpleAttributeSet();
        StyleConstants.setForeground(nameAttrs, nameColor);
        StyleConstants.setBold(nameAttrs, true);
        StyleConstants.setFontFamily(nameAttrs, "SansSerif");
        StyleConstants.setFontSize(nameAttrs, 12);

        try { doc.insertString(doc.getLength(), sender + ": ", nameAttrs); }
        catch (BadLocationException e) {}

        appendFormattedText(pane, text, textColor);
        addLinkHandlers(pane, doc);

        bubble.add(pane, BorderLayout.CENTER);
        chatContainer.add(bubble);
        chatContainer.add(Box.createVerticalStrut(4));
        chatContainer.revalidate();
    }

    /**
     * Create an RS-themed chat bubble with a colored left accent strip.
     */
    private JPanel createBubble(boolean isUser)
    {
        Color bg = isUser ? USER_BUBBLE_BG : GNOME_BUBBLE_BG;
        Color border = isUser ? USER_BUBBLE_BORDER : GNOME_BUBBLE_BORDER;
        Color accent = isUser ? USER_ACCENT : GNOME_ACCENT;

        JPanel bubble = new JPanel(new BorderLayout());
        bubble.setBackground(bg);
        bubble.setBorder(new CompoundBorder(
            new LineBorder(border, 1),
            new CompoundBorder(
                new MatteBorder(0, 3, 0, 0, accent),
                new EmptyBorder(5, 6, 5, 6)
            )
        ));
        bubble.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 8, Integer.MAX_VALUE));
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
        pane.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 16, Integer.MAX_VALUE));
        return pane;
    }

    private void appendFormattedText(JTextPane pane, String text, Color baseColor)
    {
        StyledDocument doc = pane.getStyledDocument();
        List<TextSegment> segments = parseMarkdown(text);

        for (TextSegment seg : segments)
        {
            if (seg.isLink)
            {
                SimpleAttributeSet linkAttrs = new SimpleAttributeSet();
                StyleConstants.setForeground(linkAttrs, RS_BLUE);
                StyleConstants.setUnderline(linkAttrs, true);
                StyleConstants.setFontFamily(linkAttrs, "SansSerif");
                StyleConstants.setFontSize(linkAttrs, 12);
                linkAttrs.addAttribute("url", seg.url);
                try { doc.insertString(doc.getLength(), seg.text, linkAttrs); }
                catch (BadLocationException e) {}
            }
            else
            {
                SimpleAttributeSet attrs = new SimpleAttributeSet();
                StyleConstants.setFontFamily(attrs, "SansSerif");
                StyleConstants.setFontSize(attrs, 12);

                if (seg.isBold)
                {
                    StyleConstants.setBold(attrs, true);
                    StyleConstants.setForeground(attrs, RS_WHITE);
                }
                else if (seg.isBullet)
                {
                    StyleConstants.setForeground(attrs, RS_GREEN);
                }
                else
                {
                    StyleConstants.setForeground(attrs, baseColor);
                }

                try { doc.insertString(doc.getLength(), seg.text, attrs); }
                catch (BadLocationException e) {}
            }
        }
    }

    private List<TextSegment> parseMarkdown(String text)
    {
        List<TextSegment> segments = new ArrayList<>();

        // Replace bullet markers
        text = text.replaceAll("(?m)^\\s*[-•]\\s+", "  • ");

        int pos = 0;
        while (pos < text.length())
        {
            // Wiki link [[term]]
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

            // Bold **text**
            if (text.startsWith("**", pos))
            {
                int end = text.indexOf("**", pos + 2);
                if (end >= 0)
                {
                    segments.add(new TextSegment(text.substring(pos + 2, end), false, null, true, false));
                    pos = end + 2;
                    continue;
                }
            }

            // URL
            Matcher urlMatcher = URL_PATTERN.matcher(text);
            if (urlMatcher.find(pos) && urlMatcher.start() == pos)
            {
                String url = urlMatcher.group(1);
                segments.add(new TextSegment(url, true, url, false, false));
                pos = urlMatcher.end();
                continue;
            }

            // Find next special
            int nextWiki = text.indexOf("[[", pos);
            int nextBold = text.indexOf("**", pos);
            int nextUrl = Integer.MAX_VALUE;
            Matcher um = URL_PATTERN.matcher(text);
            if (um.find(pos)) nextUrl = um.start();

            int nextSpecial = Math.min(
                nextWiki >= 0 ? nextWiki : Integer.MAX_VALUE,
                Math.min(nextBold >= 0 ? nextBold : Integer.MAX_VALUE, nextUrl)
            );
            if (nextSpecial == Integer.MAX_VALUE) nextSpecial = text.length();

            if (nextSpecial > pos)
            {
                String plain = text.substring(pos, nextSpecial);
                if (plain.contains("• "))
                {
                    String[] parts = plain.split("(• )", -1);
                    for (int i = 0; i < parts.length; i++)
                    {
                        if (!parts[i].isEmpty())
                            segments.add(new TextSegment(parts[i], false, null, false, false));
                        if (i < parts.length - 1)
                            segments.add(new TextSegment("• ", false, null, false, true));
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
