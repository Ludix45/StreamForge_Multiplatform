with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'r') as f:
    content = f.read()

import re

# We will just replace the inner Box with the Text for the generic card placeholder and make it look premium.
text_pattern = r"Box\(modifier = Modifier\.fillMaxSize\(\), contentAlignment = Alignment\.Center\) \{\s*Text\(\s*text = item\.name,\s*color = Color\.White,\s*style = MaterialTheme\.typography\.bodySmall,\s*textAlign = androidx\.compose\.ui\.text\.style\.TextAlign\.Center,\s*modifier = Modifier\.padding\(4\.dp\)\s*\)\s*\}"

new_text = """Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = item.name,
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(8.dp)
                        )
                    }"""
content = re.sub(text_pattern, new_text, content)

# Now, we also need to upgrade the Search results which might have a similar pattern. Let's do it in a safer way.
with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'w') as f:
    f.write(content)
