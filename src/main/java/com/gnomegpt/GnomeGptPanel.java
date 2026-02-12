package com.gnomegpt;

import com.gnomegpt.chat.ChatMessage;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
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
    private final JTextPane chatArea;
    private final JTextField inputField;
    private final JButton sendButton;
    private final JButton clearButton;
    private final JLabel statusLabel;
    private final StyledDocument doc;

    private static final Color USER_COLOR = new Color(0x00, 0xB0, 0x5A);
    private static final Color GNOME_COLOR = new Color(0x5D, 0xAE, 0xF8);
    private static final Color ERROR_COLOR = new Color(0xFF, 0x60, 0x60);
    private static final Color TEXT_COLOR = new Color(0xE0, 0xE0, 0xE0);
    private static final Color LINK_COLOR = new Color(0x7D, 0xC8, 0xFF);
    private static final Color COMMAND_COLOR = new Color(0xFF, 0xD7, 0x00);

    private static final Pattern URL_PATTERN = Pattern.compile(
        "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)"
    );
    private static final Pattern WIKI_TERM_PATTERN = Pattern.compile(
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
        headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        headerPanel.setBorder(new EmptyBorder(8, 10, 8, 10));

        JLabel titleLabel = new JLabel("🧒 GnomeGPT");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        clearButton = new JButton("Clear");
        clearButton.setFont(clearButton.getFont().deriveFont(11f));
        clearButton.setFocusPainted(false);
        clearButton.addActionListener(e -> plugin.clearChat());
        headerPanel.add(clearButton, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // Chat area
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        chatArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        doc = chatArea.getStyledDocument();

        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);

        // Input area
        JPanel inputPanel = new JPanel(new BorderLayout(4, 0));
        inputPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        inputPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        inputField = new JTextField();
        inputField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        inputField.setForeground(TEXT_COLOR);
        inputField.setCaretColor(TEXT_COLOR);
        inputField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(6, 8, 6, 8)
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
        sendButton.addActionListener(e -> sendMessage());

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        // Status bar
        statusLabel = new JLabel(" ");
        statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        statusLabel.setFont(statusLabel.getFont().deriveFont(10f));
        statusLabel.setBorder(new EmptyBorder(2, 8, 4, 8));

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bottomPanel.add(statusLabel, BorderLayout.NORTH);
        bottomPanel.add(inputPanel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);

        // Welcome message
        appendText("🧒 GnomeGPT\n", GNOME_COLOR, true);
        appendText("Your OSRS companion. Ask me anything!\n", TEXT_COLOR, false);
        appendText("Type /help for commands, or just chat.\n", COMMAND_COLOR, false);
        appendText("Set your API key in plugin settings to get started.\n\n", ColorScheme.LIGHT_GRAY_COLOR, false);
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
            Color nameColor;
            String prefix;

            switch (message.getRole())
            {
                case USER:
                    nameColor = USER_COLOR;
                    prefix = "You";
                    break;
                case ASSISTANT:
                    nameColor = message.getContent().startsWith("Error:") ||
                                message.getContent().startsWith("Something went wrong:")
                        ? ERROR_COLOR : GNOME_COLOR;
                    prefix = "GnomeGPT";
                    break;
                default:
                    return;
            }

            appendText(prefix + ": ", nameColor, true);
            appendTextWithLinks(message.getContent());
            appendText("\n\n", TEXT_COLOR, false);

            chatArea.setCaretPosition(doc.getLength());
        });
    }

    private void appendTextWithLinks(String text)
    {
        text = WIKI_TERM_PATTERN.matcher(text).replaceAll(match ->
        {
            String term = match.group(1);
            String url = "https://oldschool.runescape.wiki/w/" + term.replace(" ", "_");
            return term + " (" + url + ")";
        });

        Matcher matcher = URL_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find())
        {
            if (matcher.start() > lastEnd)
            {
                appendText(text.substring(lastEnd, matcher.start()), TEXT_COLOR, false);
            }
            appendLink(matcher.group(1));
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length())
        {
            appendText(text.substring(lastEnd), TEXT_COLOR, false);
        }
    }

    private void appendLink(String url)
    {
        SimpleAttributeSet linkAttrs = new SimpleAttributeSet();
        StyleConstants.setForeground(linkAttrs, LINK_COLOR);
        StyleConstants.setUnderline(linkAttrs, true);
        StyleConstants.setFontFamily(linkAttrs, "SansSerif");
        StyleConstants.setFontSize(linkAttrs, 12);
        linkAttrs.addAttribute("url", url);

        try
        {
            doc.insertString(doc.getLength(), url, linkAttrs);
            ensureLinkClickHandler();
        }
        catch (BadLocationException e)
        {
            // ignore
        }
    }

    private boolean linkHandlerAdded = false;

    private void ensureLinkClickHandler()
    {
        if (linkHandlerAdded) return;
        linkHandlerAdded = true;

        chatArea.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                int pos = chatArea.viewToModel2D(e.getPoint());
                if (pos >= 0)
                {
                    try
                    {
                        AttributeSet attrs = doc.getCharacterElement(pos).getAttributes();
                        Object url = attrs.getAttribute("url");
                        if (url != null)
                        {
                            Desktop.getDesktop().browse(new URI(url.toString()));
                        }
                    }
                    catch (Exception ex) { /* ignore */ }
                }
            }
        });

        chatArea.addMouseMotionListener(new java.awt.event.MouseMotionAdapter()
        {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e)
            {
                int pos = chatArea.viewToModel2D(e.getPoint());
                if (pos >= 0)
                {
                    AttributeSet attrs = doc.getCharacterElement(pos).getAttributes();
                    chatArea.setCursor(attrs.getAttribute("url") != null
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
            try
            {
                doc.remove(0, doc.getLength());
                appendText("Chat cleared. What's next?\n\n", ColorScheme.LIGHT_GRAY_COLOR, false);
            }
            catch (BadLocationException e) { /* ignore */ }
        });
    }

    private void appendText(String text, Color color, boolean bold)
    {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setForeground(attrs, color);
        StyleConstants.setBold(attrs, bold);
        StyleConstants.setFontFamily(attrs, "SansSerif");
        StyleConstants.setFontSize(attrs, 12);

        try
        {
            doc.insertString(doc.getLength(), text, attrs);
        }
        catch (BadLocationException e) { /* ignore */ }
    }
}
