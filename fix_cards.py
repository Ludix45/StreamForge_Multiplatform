import re

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'r') as f:
    content = f.read()

pattern = r"(Card\([\s\S]*?modifier = Modifier[\s\S]*?\.clickable \{[\s\S]*?\}[\s\S]*?\) \{)\s*(Box\(modifier = Modifier\.fillMaxSize\(\), contentAlignment = Alignment\.Center\) \{[\s\S]*?AsyncImage\([\s\S]*?contentScale = ContentScale\.Crop\s*\))"

def replacement(match):
    return match.group(1) + "\n                    Box(modifier = Modifier.fillMaxSize()) {\n                        " + match.group(2).replace("\n", "\n    ") + "\n                    }"

# Oh wait, an easier way is to just replace the first Box with a Box that doesn't fill max size, or put them both in a Box inside Card.
