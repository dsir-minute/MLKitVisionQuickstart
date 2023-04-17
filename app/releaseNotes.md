Intelligent posture assistant. MLKit variant.

I am the TCR Electroshaker Lab's intelligent assistant, I help a person to do physical activities (like driving a car)
while avoiding any body pain due to non ergonomic postures.

derived from https://github.com/googlesamples/mlkit/tree/master/android/vision-quickstart
- get gpu acceleration: free w/ MLKit
- draw only selected lines and landmarks 
- use buildconfig consts
- add side selector in settings
- show landmarks on selected side only
TODO:
- get person's height
- get target/optimal knee and elbow angles
- process height and optimum angles
- tts prepared (https://www.tutorialkart.com/kotlin-android/android-text-to-speech-kotlin-example/)
- long tap somewhere to quit
- play starting sounds (https://www.geeksforgeeks.org/how-to-play-audio-from-url-in-android/, https://stackoverflow.com/questions/7499605/how-to-play-the-audio-files-directly-from-res-raw-folder)
- compute and display elbow angle
- compute and display knee angle
- portrait locked
- non flickering display of cumulative averages of elbow & knee angles
- new tools icon, show optima angles near current ones
- add spoken instructions to the report on the angles
- indicate visually/audibly if target angles missed or reached
- give visual/audible instructions to reach target angles

Proudly Crafted by raxy in Kotlin
