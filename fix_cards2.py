with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'r') as f:
    content = f.read()

import re

# Find the exact pattern in HomeCarousel
old_card_content = """                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = item.name,
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = "Poster of ${item.name}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }"""

new_card_content = """                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = item.name,
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        AsyncImage(
                            model = item.posterUrl,
                            contentDescription = "Poster of ${item.name}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }"""

content = content.replace(old_card_content, new_card_content)

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'w') as f:
    f.write(content)
