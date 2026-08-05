with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'r') as f:
    content = f.read()

import re

hero_pattern = r"Box\(\s*modifier = Modifier\s*\.fillMaxWidth\(\)\s*\.height\(350\.dp\)\s*\.background\(Color\.DarkGray\)"
new_hero = """Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(450.dp)
                        .background(DarkSurface)"""
content = re.sub(hero_pattern, new_hero, content)

gradient_pattern = r"Brush\.verticalGradient\(\s*colors = listOf\(Color\.Transparent, DarkBackground\),\s*startY = 100f\s*\)"
new_gradient = """Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xAA000000), DarkBackground),
                                    startY = 0f
                                )"""
content = re.sub(gradient_pattern, new_gradient, content)

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'w') as f:
    f.write(content)
