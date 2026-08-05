import re

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'r') as f:
    content = f.read()

# Replace any other Box inside Card that might have the same issue. Let's make sure Favorites and Continue Watching are fine.
