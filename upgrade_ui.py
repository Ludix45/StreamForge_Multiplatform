import re

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'r') as f:
    content = f.read()

# Make the bottom bar nicer
nav_bar_replacement = """
                    NavigationBar(
                        containerColor = DarkBackground,
                        contentColor = Color.White,
                        tonalElevation = 8.dp
                    ) {
"""
content = content.replace("""                    NavigationBar(
                        containerColor = DarkSurface,
                        contentColor = Color.White
                    ) {""", nav_bar_replacement)

# Make poster cards more premium
box_pattern = r"Box\(\s*modifier = Modifier\s*\.width\(120\.dp\)\s*\.height\(180\.dp\)\s*\.clip\(RoundedCornerShape\(8\.dp\)\)\s*\.background\(Color\.DarkGray\)"

premium_box = """Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .width(130.dp)
                        .height(195.dp)"""
content = re.sub(box_pattern, premium_box, content)

# Change Text(item.name) inside to have a gradient overlay
# Wait, this is tricky to regex accurately. I will use a more robust way.

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'w') as f:
    f.write(content)
