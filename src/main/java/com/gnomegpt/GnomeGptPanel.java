package com.gnomegpt;

import com.gnomegpt.chat.ChatMessage;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.Desktop;
import java.net.URI;
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

    private static final Color USER_BG = new Color(0x2D, 0x50, 0x3C);
    private static final Color GNOME_BG = new Color(0x2B, 0x3A, 0x52);
    private static final Color ERROR_BG = new Color(0x52, 0x2B, 0x2B);
    private static final Color TEXT_COLOR = new Color(0xE0, 0xE0, 0xE0);
    private static final Color LINK_COLOR = new Color(0x7D, 0xC8, 0xFF);
    private static final Color DIM_COLOR = new Color(0x90, 0x90, 0x90);

    private static final Pattern URL_PATTERN = Pattern.compile(
        "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)"
    );
    private static final Pattern WIKI_LINK_PATTERN = Pattern.compile(
        "\\[\\[([^\\]]+)\\]\\]"
    );

    public GnomeGptPanel(GnomeGptPlugin plugin)
    {
        super(false);
        this.plugin = plugin;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(0x1B, 0x1B, 0x2B));
        headerPanel.setBorder(new EmptyBorder(6, 10, 6, 10));
        headerPanel.setPreferredSize(new Dimension(0, 32));

        JLabel titleLabel = new JLabel("\uD83E\uDDD2 GnomeGPT");
        titleLabel.setForeground(new Color(0xA0, 0xD0, 0xFF));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JButton clearButton = new JButton("Clear");
        clearButton.setFont(clearButton.getFont().deriveFont(10f));
        clearButton.setFocusPainted(false);
        clearButton.setMargin(new Insets(2, 6, 2, 6));
        clearButton.addActionListener(e -> plugin.clearChat());
        headerPanel.add(clearButton, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // Chat area — vertical box of message bubbles
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

        // Input area
        JPanel inputPanel = new JPanel(new BorderLayout(4, 0));
        inputPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        inputPanel.setBorder(new EmptyBorder(6, 6, 6, 6));

        inputField = new JTextField();
        inputField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        inputField.setForeground(TEXT_COLOR);
        inputField.setCaretColor(TEXT_COLOR);
        inputField.setBorder(new CompoundBorder(
            new LineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1),
            new EmptyBorder(4, 6, 4, 6)
        ));
        inputField.setFont(new Font("SansSerif", Font.PLAIN, 12));

        inputField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "send");
        inputField.getActionMap().put("send", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                sendMessage();
            }
        });

        sendButton = new JButton("Send");
        sendButton.setFocusPainted(false);
        sendButton.setMargin(new Insets(4, 8, 4, 8));
        sendButton.addActionListener(e -> sendMessage());

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        // Status
        statusLabel = new JLabel(" ");
        statusLabel.setForeground(DIM_COLOR);
        statusLabel.setFont(statusLabel.getFont().deriveFont(10f));
        statusLabel.setBorder(new EmptyBorder(2, 8, 2, 8));

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bottomPanel.add(statusLabel, BorderLayout.NORTH);
        bottomPanel.add(inputPanel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);

        // Welcome
        addBubble("Your OSRS companion. Ask me anything, or type /help for commands.\nSet your API key in plugin settings to get started.", GNOME_BG, "GnomeGPT");
    }

    private void sendMessage()
    {
        String text = inputField.getText().trim();
        if (!text.isEmpty())
        {
            inputField.setText("");
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

            // Scroll to bottom
            SwingUtilities.invokeLater(() ->
            {
                JScrollBar sb = scrollPane.getVerticalScrollBar();
                sb.setValue(sb.getMaximum());
            });
        });
    }

    private void addBubble(String text, Color bgColor, String sender)
    {
        JPanel bubble = new JPanel();
        bubble.setLayout(new BorderLayout());
        bubble.setBackground(bgColor);
        bubble.setBorder(new CompoundBorder(
            new EmptyBorder(2, 0, 2, 0),
            new CompoundBorder(
                new LineBorder(bgColor.darker(), 1, true),
                new EmptyBorder(6, 8, 6, 8)
            )
        ));

        // Sender label
        JLabel senderLabel = new JLabel(sender);
        senderLabel.setForeground(sender.equals("You") ? new Color(0x60, 0xD0, 0x80) : new Color(0x80, 0xBB, 0xF0));
        senderLabel.setFont(senderLabel.getFont().deriveFont(Font.BOLD, 11f));
        senderLabel.setBorder(new EmptyBorder(0, 0, 3, 0));
        bubble.add(senderLabel, BorderLayout.NORTH);

        // Message body — use JTextPane for clickable links
        JTextPane textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setOpaque(false);
        textPane.setFont(new Font("SansSerif", Font.PLAIN, 12));
        textPane.setBorder(null);

        // Process wiki links and URLs
        appendFormattedText(textPane, text);

        // Make it wrap properly within the sidebar
        textPane.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 30, Integer.MAX_VALUE));

        bubble.add(textPane, BorderLayout.CENTER);

        // Size constraints
        bubble.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 12, Integer.MAX_VALUE));
        bubble.setAlignmentX(Component.LEFT_ALIGNMENT);

        chatContainer.add(bubble);
        chatContainer.add(Box.createVerticalStrut(4));
        chatContainer.revalidate();
    }

    private void appendFormattedText(JTextPane pane, String text)
    {
        javax.swing.text.StyledDocument doc = pane.getStyledDocument();

        // First convert [[wiki terms]] to inline text with wiki URLs stored
        // We'll process the text and insert styled segments

        // Replace [[Term]] with just the term text, but make it a clickable link
        StringBuilder processed = new StringBuilder();
        java.util.List<int[]> linkRanges = new java.util.ArrayList<>();
        java.util.List<String> linkUrls = new java.util.ArrayList<>();

        Matcher wikiMatcher = WIKI_LINK_PATTERN.matcher(text);
        int lastEnd = 0;
        while (wikiMatcher.find())
        {
            processed.append(text, lastEnd, wikiMatcher.start());
            int linkStart = processed.length();
            String term = wikiMatcher.group(1);
            processed.append(term);
            linkRanges.add(new int[]{linkStart, processed.length()});
            linkUrls.add("https://oldschool.runescape.wiki/w/" + term.replace(" ", "_"));
            lastEnd = wikiMatcher.end();
        }
        processed.append(text.substring(lastEnd));

        String cleanText = processed.toString();

        // Now find regular URLs in the clean text
        Matcher urlMatcher = URL_PATTERN.matcher(cleanText);
        while (urlMatcher.find())
        {
            // Check if this URL is already part of a wiki link range
            boolean alreadyCovered = false;
            for (int[] range : linkRanges)
            {
                if (urlMatcher.start() >= range[0] && urlMatcher.end() <= range[1])
                {
                    alreadyCovered = true;
                    break;
                }
            }
            if (!alreadyCovered)
            {
                linkRanges.add(new int[]{urlMatcher.start(), urlMatcher.end()});
                linkUrls.add(urlMatcher.group(1));
            }
        }

        // Insert text with styles
        javax.swing.text.SimpleAttributeSet normalAttrs = new javax.swing.text.SimpleAttributeSet();
        javax.swing.text.StyleConstants.setForeground(normalAttrs, TEXT_COLOR);
        javax.swing.text.StyleConstants.setFontFamily(normalAttrs, "SansSerif");
        javax.swing.text.StyleConstants.setFontSize(normalAttrs, 12);

        try
        {
            // Sort link ranges by start position
            java.util.List<Integer> indices = new java.util.ArrayList<>();
            for (int i = 0; i < linkRanges.size(); i++) indices.add(i);
            indices.sort((a, b) -> linkRanges.get(a)[0] - linkRanges.get(b)[0]);

            int pos = 0;
            for (int idx : indices)
            {
                int[] range = linkRanges.get(idx);
                String url = linkUrls.get(idx);

                // Normal text before link
                if (range[0] > pos)
                {
                    doc.insertString(doc.getLength(), cleanText.substring(pos, range[0]), normalAttrs);
                }

                // Link text
                javax.swing.text.SimpleAttributeSet linkAttrs = new javax.swing.text.SimpleAttributeSet();
                javax.swing.text.StyleConstants.setForeground(linkAttrs, LINK_COLOR);
                javax.swing.text.StyleConstants.setUnderline(linkAttrs, true);
                javax.swing.text.StyleConstants.setFontFamily(linkAttrs, "SansSerif");
                javax.swing.text.StyleConstants.setFontSize(linkAttrs, 12);
                linkAttrs.addAttribute("url", url);

                doc.insertString(doc.getLength(), cleanText.substring(range[0], range[1]), linkAttrs);

                pos = range[1];
            }

            // Remaining text
            if (pos < cleanText.length())
            {
                doc.insertString(doc.getLength(), cleanText.substring(pos), normalAttrs);
            }
        }
        catch (javax.swing.text.BadLocationException e)
        {
            // fallback
            try { doc.insertString(0, cleanText, normalAttrs); } catch (Exception ex) {}
        }

        // Click handler for links
        pane.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                int clickPos = pane.viewToModel2D(e.getPoint());
                if (clickPos >= 0)
                {
                    javax.swing.text.AttributeSet attrs = doc.getCharacterElement(clickPos).getAttributes();
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
                    javax.swing.text.AttributeSet attrs = doc.getCharacterElement(hoverPos).getAttributes();
                    pane.setCursor(attrs.getAttribute("url") != null
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
                }
            }
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
            addBubble("Chat cleared.", GNOME_BG, "GnomeGPT");
            chatContainer.revalidate();
            chatContainer.repaint();
        });
    }
}
