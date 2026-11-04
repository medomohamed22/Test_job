package com.tripmate.ai.data.seed

import com.tripmate.ai.domain.model.ArtMotif
import com.tripmate.ai.domain.model.CatalogPlace
import com.tripmate.ai.domain.model.Destination
import com.tripmate.ai.domain.model.Interest.ART
import com.tripmate.ai.domain.model.Interest.FAMILY
import com.tripmate.ai.domain.model.Interest.FOOD
import com.tripmate.ai.domain.model.Interest.HISTORY
import com.tripmate.ai.domain.model.Interest.MUSEUMS
import com.tripmate.ai.domain.model.Interest.NATURE
import com.tripmate.ai.domain.model.Interest.PHOTOGRAPHY
import com.tripmate.ai.domain.model.Interest.SHOPPING
import com.tripmate.ai.domain.model.LocalizedGuide
import com.tripmate.ai.domain.model.StopCategory
import com.tripmate.ai.domain.model.WalkingLevel

/**
 * Curated seed data that makes the app fully usable with no API keys.
 *
 * - Coordinates are approximate public landmark locations.
 * - Opening windows and prices are ILLUSTRATIVE samples, never live data, and the UI says so.
 * - Guide texts are short, conservative summaries of widely documented facts written for this
 *   project (no third-party text is copied). They are labelled "Saved offline content".
 */
object PlaceCatalog {

    val destinations = listOf(
        Destination("cairo", "Cairo", "القاهرة", "Egypt", "مصر", 30.0444, 31.2357, ArtMotif.MOSQUE, true),
        Destination("giza", "Giza", "الجيزة", "Egypt", "مصر", 29.9870, 31.2118, ArtMotif.PYRAMIDS, true),
        Destination("alexandria", "Alexandria", "الإسكندرية", "Egypt", "مصر", 31.2001, 29.9187, ArtMotif.SEA, true),
    )

    fun destination(id: String) = destinations.firstOrNull { it.id == id }
    fun place(id: String?) = places.firstOrNull { it.id == id }
    fun forDestination(id: String) = places.filter { id in it.destinationIds }

    private val CG = setOf("cairo", "giza")
    private val ALX = setOf("alexandria")
    private fun h(hh: Int, mm: Int = 0) = hh * 60 + mm

    val places: List<CatalogPlace> = listOf(
        CatalogPlace(
            id = "giza_pyramids", destinationIds = CG, nameEn = "Pyramids of Giza", nameAr = "أهرامات الجيزة", area = "giza_plateau",
            lat = 29.9792, lng = 31.1342, interests = setOf(HISTORY, PHOTOGRAPHY, FAMILY), category = StopCategory.SIGHT,
            typicalDurationMinutes = 150, minDurationMinutes = 90, priority = 3, typicalOpens = h(8), typicalCloses = h(17),
            priceEstimate = 700.0, motif = ArtMotif.PYRAMIDS, walkingIntensity = WalkingLevel.ACTIVE,
            guideEn = LocalizedGuide(
                short = "The three great pyramids were built about 4,500 years ago, during Egypt's Old Kingdom, as royal tombs for the pharaohs Khufu, Khafre and Menkaure.",
                detailed = "The Giza pyramid complex dates to Egypt's Fourth Dynasty. The Great Pyramid of Khufu is the largest, and it is the only one of the Seven Wonders of the Ancient World still standing. Each pyramid was part of a wider funerary complex with temples, causeways and smaller pyramids for queens. Exactly how they were built is still studied by archaeologists, so treat confident single answers with care.",
                kids = "Imagine stacking millions of giant stone blocks — some heavier than a car — without modern machines! These pyramids were built as resting places for ancient kings, and they are older than almost anything else you will ever visit.",
                lookFor = listOf("The size of the individual limestone blocks at the base", "The small queens' pyramids beside the big ones", "The panoramic viewpoint where all the pyramids line up", "Traces of the smooth outer casing near the top of Khafre's pyramid"),
                tips = "Go early to beat heat and crowds, carry water and sun protection, buy tickets only at official windows, and agree any camel or horse ride price before you start.",
            ),
            guideAr = LocalizedGuide(
                short = "بُنيت الأهرامات الثلاثة الكبرى قبل نحو 4500 عام في عصر الدولة القديمة، لتكون مقابر ملكية للملوك خوفو وخفرع ومنكاورع.",
                detailed = "يعود مجمع أهرامات الجيزة إلى الأسرة الرابعة. الهرم الأكبر للملك خوفو هو الأضخم، وهو العجيبة الوحيدة الباقية من عجائب الدنيا السبع القديمة. كان كل هرم جزءًا من مجمع جنائزي يضم معابد وطرقًا صاعدة وأهرامات صغيرة للملكات. ولا تزال طريقة البناء موضع دراسة لدى علماء الآثار، لذا تعامل بحذر مع أي إجابة قاطعة واحدة.",
                kids = "تخيّل أن تكدّس ملايين الحجارة الضخمة — بعضها أثقل من سيارة — بدون آلات حديثة! بُنيت هذه الأهرامات لتكون مثوى لملوك مصر القديمة، وهي أقدم من أي مكان تقريبًا ستزوره.",
                lookFor = listOf("حجم الكتل الحجرية الجيرية عند القاعدة", "أهرامات الملكات الصغيرة بجوار الأهرامات الكبرى", "نقطة المشاهدة البانورامية التي تظهر فيها الأهرامات متتالية", "بقايا الكسوة الملساء قرب قمة هرم خفرع"),
                tips = "اذهب مبكرًا لتجنّب الحر والزحام، واحمل الماء وواقي الشمس، واشترِ التذاكر من الشبابيك الرسمية فقط، واتفق على سعر ركوب الجمل أو الحصان قبل البدء.",
            ),
        ),
        CatalogPlace(
            id = "giza_sphinx", destinationIds = CG, nameEn = "Great Sphinx of Giza", nameAr = "تمثال أبو الهول", area = "giza_plateau",
            lat = 29.9753, lng = 31.1376, interests = setOf(HISTORY, PHOTOGRAPHY, FAMILY), category = StopCategory.SIGHT,
            typicalDurationMinutes = 45, minDurationMinutes = 25, priority = 3, typicalOpens = h(8), typicalCloses = h(17),
            priceEstimate = null, motif = ArtMotif.SPHINX, walkingIntensity = WalkingLevel.MODERATE,
            guideEn = LocalizedGuide(
                short = "The Sphinx, with a lion's body and a human head, was carved directly from the bedrock of the Giza plateau and is widely attributed to the reign of Khafre.",
                detailed = "Unlike the pyramids, the Sphinx was not assembled from blocks: it was sculpted out of the natural limestone, with some masonry added later during repairs. Most Egyptologists link it to King Khafre because of its position beside his causeway and valley temple. Its weathered layers show how different rock beds have eroded at different speeds.",
                kids = "This is a giant statue with the body of a lion and the head of a person, carved out of one huge piece of rock. It has been 'guarding' the pyramids for thousands of years!",
                lookFor = listOf("The valley temple right next to the Sphinx", "Different erosion layers along its body", "How it lines up with the causeway toward Khafre's pyramid"),
                tips = "Access is usually through the plateau ticket area. The classic photo spot is the viewing terrace in front of the statue; it is busiest late morning.",
            ),
            guideAr = LocalizedGuide(
                short = "أبو الهول تمثال بجسم أسد ورأس إنسان، نُحت مباشرة في صخر هضبة الجيزة، ويُنسب على نطاق واسع إلى عهد الملك خفرع.",
                detailed = "على عكس الأهرامات، لم يُبنَ أبو الهول من كتل، بل نُحت في الحجر الجيري الطبيعي، وأُضيفت إليه بعض الكتل لاحقًا أثناء الترميم. يربطه معظم علماء المصريات بالملك خفرع بسبب موقعه بجوار طريقه الصاعد ومعبد الوادي. وتُظهر طبقاته المتآكلة كيف تآكلت طبقات الصخر بسرعات مختلفة.",
                kids = "هذا تمثال عملاق له جسم أسد ورأس إنسان، منحوت من قطعة صخر واحدة ضخمة. وهو 'يحرس' الأهرامات منذ آلاف السنين!",
                lookFor = listOf("معبد الوادي المجاور لأبي الهول", "طبقات التآكل المختلفة على جسمه", "اصطفافه مع الطريق الصاعد نحو هرم خفرع"),
                tips = "يكون الدخول عادة من منطقة تذاكر الهضبة. أفضل مكان للتصوير هو المصطبة أمام التمثال، وتزدحم في أواخر الصباح.",
            ),
        ),
        CatalogPlace(
            id = "gem", destinationIds = CG, nameEn = "Grand Egyptian Museum", nameAr = "المتحف المصري الكبير", area = "giza_plateau",
            lat = 29.9946, lng = 31.1196, interests = setOf(MUSEUMS, HISTORY, ART, FAMILY), category = StopCategory.SIGHT,
            typicalDurationMinutes = 180, minDurationMinutes = 120, priority = 3, typicalOpens = h(9), typicalCloses = h(18),
            priceEstimate = 1200.0, motif = ArtMotif.MUSEUM, walkingIntensity = WalkingLevel.MODERATE,
            guideEn = LocalizedGuide(
                short = "A vast modern museum near the pyramids dedicated to ancient Egyptian civilisation, known for its grand staircase and its extensive Tutankhamun galleries.",
                detailed = "The Grand Egyptian Museum was designed as one of the largest museums in the world devoted to a single civilisation. A colossal statue of Ramesses II greets visitors in the atrium, and the Grand Staircase is lined with royal statues leading toward views of the pyramids. The Tutankhamun galleries bring together a large part of the young king's burial equipment.",
                kids = "This museum is so big you could get lost! Look for the giant statue of a king in the entrance hall and the golden treasures of Tutankhamun, a boy who became pharaoh.",
                lookFor = listOf("The colossal Ramesses II statue in the atrium", "The view of the pyramids from the top of the Grand Staircase", "Tutankhamun's chariots and jewellery"),
                tips = "Plan at least three hours and check current ticketing and timed-entry rules before you go; sections may differ in opening times.",
            ),
            guideAr = LocalizedGuide(
                short = "متحف حديث ضخم بجوار الأهرامات مخصص للحضارة المصرية القديمة، يشتهر بالدرج العظيم وقاعات توت عنخ آمون الواسعة.",
                detailed = "صُمم المتحف المصري الكبير ليكون من أكبر متاحف العالم المخصصة لحضارة واحدة. يستقبل الزوار تمثال ضخم لرمسيس الثاني في البهو، ويصطف على الدرج العظيم عدد من التماثيل الملكية وصولًا إلى إطلالة على الأهرامات. وتجمع قاعات توت عنخ آمون جزءًا كبيرًا من مقتنيات دفن الملك الشاب.",
                kids = "هذا المتحف كبير جدًا لدرجة أنك قد تتوه فيه! ابحث عن تمثال الملك العملاق في البهو، وكنوز توت عنخ آمون الذهبية، الصبي الذي أصبح فرعونًا.",
                lookFor = listOf("تمثال رمسيس الثاني الضخم في البهو", "إطلالة الأهرامات من أعلى الدرج العظيم", "عربات توت عنخ آمون ومجوهراته"),
                tips = "خصص ثلاث ساعات على الأقل، وتحقق من نظام التذاكر ومواعيد الدخول الحالية قبل الذهاب؛ فقد تختلف مواعيد بعض الأقسام.",
            ),
        ),
        CatalogPlace(
            id = "egyptian_museum", destinationIds = CG, nameEn = "Egyptian Museum (Tahrir)", nameAr = "المتحف المصري بالتحرير", area = "downtown",
            lat = 30.0478, lng = 31.2336, interests = setOf(MUSEUMS, HISTORY, ART), category = StopCategory.SIGHT,
            typicalDurationMinutes = 150, minDurationMinutes = 90, priority = 2, typicalOpens = h(9), typicalCloses = h(17),
            priceEstimate = 550.0, motif = ArtMotif.MUSEUM, walkingIntensity = WalkingLevel.MODERATE,
            guideEn = LocalizedGuide(
                short = "Opened in 1902, this landmark building in Tahrir Square holds one of the world's richest collections of ancient Egyptian antiquities.",
                detailed = "The Egyptian Museum's neoclassical building has been a centre of Egyptology for over a century. Its galleries are arranged broadly by period, from early dynastic objects through the Old, Middle and New Kingdoms to the Greco-Roman era. Some famous objects have moved to newer museums, so the displays continue to change.",
                kids = "This pink building is like a time machine full of statues, painted coffins and ancient jewellery. Can you spot a statue that looks like it's smiling?",
                lookFor = listOf("Old Kingdom statues with remarkably lifelike eyes", "Art from the Amarna period with its unusual, rounded style", "Greco-Roman mummy portraits painted on wood"),
                tips = "The building is large and older, so wear comfortable shoes. Check which galleries are currently open, as objects are being relocated.",
            ),
            guideAr = LocalizedGuide(
                short = "افتُتح عام 1902، ويضم هذا المبنى الشهير في ميدان التحرير واحدة من أغنى مجموعات الآثار المصرية القديمة في العالم.",
                detailed = "ظل مبنى المتحف المصري ذو الطراز الكلاسيكي الجديد مركزًا لعلم المصريات لأكثر من قرن. تُعرض القاعات بحسب الحقب تقريبًا، من عصر الأسرات المبكر مرورًا بالدولة القديمة والوسطى والحديثة وحتى العصر اليوناني الروماني. وقد نُقلت بعض القطع الشهيرة إلى متاحف أحدث، لذا تتغير المعروضات باستمرار.",
                kids = "هذا المبنى الوردي يشبه آلة زمن مليئة بالتماثيل والتوابيت الملونة والمجوهرات القديمة. هل تستطيع أن تجد تمثالًا يبدو مبتسمًا؟",
                lookFor = listOf("تماثيل الدولة القديمة ذات العيون الواقعية بشكل مدهش", "فن عصر العمارنة بأسلوبه المستدير المختلف", "بورتريهات المومياوات اليونانية الرومانية المرسومة على الخشب"),
                tips = "المبنى كبير وقديم، فارتدِ حذاءً مريحًا. وتحقق من القاعات المفتوحة حاليًا لأن بعض القطع يجري نقلها.",
            ),
        ),
        CatalogPlace(
            id = "citadel", destinationIds = CG, nameEn = "Cairo Citadel & Muhammad Ali Mosque", nameAr = "قلعة صلاح الدين ومسجد محمد علي", area = "islamic_cairo",
            lat = 30.0287, lng = 31.2599, interests = setOf(HISTORY, PHOTOGRAPHY, ART), category = StopCategory.SIGHT,
            typicalDurationMinutes = 120, minDurationMinutes = 75, priority = 2, typicalOpens = h(8), typicalCloses = h(17),
            priceEstimate = 550.0, motif = ArtMotif.MOSQUE, walkingIntensity = WalkingLevel.MODERATE,
            guideEn = LocalizedGuide(
                short = "A medieval hilltop fortress begun by Salah al-Din in the 12th century, crowned by the 19th-century Ottoman-style Mosque of Muhammad Ali.",
                detailed = "Salah al-Din started fortifying this spur of the Muqattam hills in the 1170s, and the Citadel served as the seat of government for centuries. Its skyline is dominated by the Mosque of Muhammad Ali, built in the first half of the 19th century in an Ottoman style and often called the Alabaster Mosque because of its stone cladding. The terraces offer wide views across the city.",
                kids = "This is a real castle on a hill! Kings and rulers lived and worked here, and the mosque on top has huge domes and tall, thin minarets like pencils.",
                lookFor = listOf("The alabaster-clad courtyard and its clock tower", "The pencil-thin Ottoman minarets", "The city panorama from the terrace on a clear day"),
                tips = "Dress modestly for the mosque (shoulders and knees covered); shoes are removed inside. Late afternoon light is lovely for photos.",
            ),
            guideAr = LocalizedGuide(
                short = "قلعة من العصور الوسطى على تل، بدأ بناءها صلاح الدين في القرن الثاني عشر، ويتوّجها مسجد محمد علي ذو الطراز العثماني من القرن التاسع عشر.",
                detailed = "بدأ صلاح الدين تحصين هذا الجزء من تلال المقطم في سبعينيات القرن الثاني عشر، وظلت القلعة مقرًا للحكم لقرون. ويهيمن على أفقها مسجد محمد علي الذي بُني في النصف الأول من القرن التاسع عشر على الطراز العثماني، ويُسمى غالبًا مسجد المرمر بسبب كسوته الحجرية. وتوفر الشرفات إطلالات واسعة على المدينة.",
                kids = "هذه قلعة حقيقية فوق تل! عاش فيها الحكام وعملوا، والمسجد في أعلاها له قباب ضخمة ومآذن رفيعة وطويلة مثل أقلام الرصاص.",
                lookFor = listOf("الصحن المكسو بالمرمر وبرج الساعة", "المآذن العثمانية الرفيعة", "بانوراما المدينة من الشرفة في يوم صافٍ"),
                tips = "ارتدِ ملابس محتشمة لدخول المسجد (تغطية الكتفين والركبتين)، وتُخلع الأحذية بالداخل. ضوء العصر رائع للتصوير.",
            ),
        ),
        CatalogPlace(
            id = "khan_khalili", destinationIds = CG, nameEn = "Khan el-Khalili Bazaar", nameAr = "خان الخليلي", area = "islamic_cairo",
            lat = 30.0477, lng = 31.2623, interests = setOf(SHOPPING, FOOD, PHOTOGRAPHY), category = StopCategory.SIGHT,
            typicalDurationMinutes = 90, minDurationMinutes = 45, priority = 2, typicalOpens = h(10), typicalCloses = h(23),
            priceEstimate = null, motif = ArtMotif.BAZAAR, walkingIntensity = WalkingLevel.MODERATE,
            guideEn = LocalizedGuide(
                short = "A lively historic bazaar whose origins go back to the late 14th century, packed with lanterns, spices, metalwork and traditional cafés.",
                detailed = "Khan el-Khalili grew around a caravanserai founded in the Mamluk period and became one of Cairo's great commercial quarters. Today its lanes mix workshops, souvenir stalls and old coffeehouses, and it sits beside landmarks of historic Cairo such as Al-Hussein Mosque and Al-Azhar.",
                kids = "A maze of tiny streets full of shiny lamps, colourful scarves and the smell of spices. Count how many different lantern colours you can find!",
                lookFor = listOf("Pierced brass lanterns glowing in the evening", "Copper and silver workshops still in use", "Medieval gateways at the edges of the market"),
                tips = "Bargain politely and with a smile, keep valuables secure in crowds, and ask before photographing people or their stalls.",
            ),
            guideAr = LocalizedGuide(
                short = "سوق تاريخي نابض بالحياة تعود جذوره إلى أواخر القرن الرابع عشر، مليء بالفوانيس والتوابل والمشغولات المعدنية والمقاهي التقليدية.",
                detailed = "نشأ خان الخليلي حول خان للقوافل أُسس في العصر المملوكي، وأصبح من أهم الأحياء التجارية في القاهرة. وتمتزج في أزقته اليوم الورش ومحلات الهدايا والمقاهي القديمة، ويقع بجوار معالم القاهرة التاريخية مثل مسجد الحسين والجامع الأزهر.",
                kids = "متاهة من الشوارع الصغيرة المليئة بالفوانيس اللامعة والأوشحة الملونة ورائحة التوابل. كم لونًا مختلفًا من الفوانيس يمكنك أن تعدّ؟",
                lookFor = listOf("الفوانيس النحاسية المخرّمة المضيئة مساءً", "ورش النحاس والفضة التي ما زالت تعمل", "البوابات التاريخية على أطراف السوق"),
                tips = "فاصل في السعر بلطف وابتسامة، واحفظ متعلقاتك الثمينة في الزحام، واستأذن قبل تصوير الأشخاص أو محلاتهم.",
            ),
        ),
        CatalogPlace(
            id = "muizz_street", destinationIds = CG, nameEn = "Al-Muizz Street", nameAr = "شارع المعز لدين الله", area = "islamic_cairo",
            lat = 30.0517, lng = 31.2617, interests = setOf(HISTORY, PHOTOGRAPHY, ART), category = StopCategory.SIGHT,
            typicalDurationMinutes = 75, minDurationMinutes = 40, priority = 2, typicalOpens = null, typicalCloses = null,
            priceEstimate = null, motif = ArtMotif.MOSQUE, walkingIntensity = WalkingLevel.ACTIVE,
            guideEn = LocalizedGuide(
                short = "The historic spine of Fatimid Cairo, lined with one of the densest concentrations of medieval Islamic architecture anywhere.",
                detailed = "Al-Muizz Street dates back to the founding of Fatimid Cairo in the 10th century and lies within the 'Historic Cairo' UNESCO World Heritage Site. Walking it, you pass mosques, madrasas, sabil-kuttabs (public fountains with schools above) and mansions from the Fatimid, Mamluk and Ottoman periods.",
                kids = "This old street is like an outdoor museum. Look up — the buildings have carved stone patterns, wooden window screens and towers called minarets.",
                lookFor = listOf("Carved wooden mashrabiya window screens", "Sabil-kuttab fountain buildings", "The Qalawun complex's striped stone façade"),
                tips = "Best in late afternoon or evening when it is cooler and lit up. Some monuments need separate tickets; respect prayer times in mosques.",
            ),
            guideAr = LocalizedGuide(
                short = "العمود الفقري التاريخي للقاهرة الفاطمية، ويضم واحدًا من أكثف تجمعات العمارة الإسلامية في العصور الوسطى في العالم.",
                detailed = "يعود شارع المعز إلى تأسيس القاهرة الفاطمية في القرن العاشر، ويقع ضمن موقع 'القاهرة التاريخية' المدرج في التراث العالمي لليونسكو. وأثناء السير فيه تمر بمساجد ومدارس وأسبلة وكتاتيب وبيوت من العصور الفاطمية والمملوكية والعثمانية.",
                kids = "هذا الشارع القديم يشبه متحفًا مفتوحًا. انظر إلى الأعلى — المباني فيها زخارف حجرية منحوتة ومشربيات خشبية وأبراج تسمى المآذن.",
                lookFor = listOf("المشربيات الخشبية المنحوتة", "مباني السبيل والكُتّاب", "واجهة مجموعة قلاوون الحجرية المخططة"),
                tips = "أجمل وقت هو العصر أو المساء حين يكون الجو ألطف والإضاءة جميلة. بعض الآثار تحتاج تذاكر منفصلة، واحترم أوقات الصلاة في المساجد.",
            ),
        ),
        CatalogPlace(
            id = "azhar_park", destinationIds = CG, nameEn = "Al-Azhar Park", nameAr = "حديقة الأزهر", area = "islamic_cairo",
            lat = 30.0406, lng = 31.2643, interests = setOf(NATURE, FAMILY, PHOTOGRAPHY), category = StopCategory.SIGHT,
            typicalDurationMinutes = 75, minDurationMinutes = 40, priority = 1, typicalOpens = h(9), typicalCloses = h(22),
            priceEstimate = 50.0, motif = ArtMotif.GARDEN, walkingIntensity = WalkingLevel.MODERATE,
            guideEn = LocalizedGuide(
                short = "A green hilltop park opened in 2005 on land that had been a rubble mound for centuries, with sweeping views over historic Cairo.",
                detailed = "Al-Azhar Park was developed by the Aga Khan Trust for Culture. Creating it uncovered a long stretch of the medieval Ayyubid city wall along its western edge. Its formal gardens, water channels and viewpoints face the Citadel and the minarets of historic Cairo.",
                kids = "A big garden on a hill where you can run on the grass and see the whole old city with its domes and towers.",
                lookFor = listOf("The skyline of minarets at sunset", "The restored Ayyubid wall on the park's edge", "Water channels inspired by Islamic garden design"),
                tips = "Sunset is the most popular time. It is a good, calm spot for a break between busy sights.",
            ),
            guideAr = LocalizedGuide(
                short = "حديقة خضراء فوق تل افتُتحت عام 2005 على أرض ظلت تلًا من الركام لقرون، وتطل على القاهرة التاريخية.",
                detailed = "طوّرت حديقة الأزهر مؤسسة الآغا خان للثقافة. وكشف إنشاؤها عن جزء طويل من سور المدينة الأيوبي على حافتها الغربية. وتواجه حدائقها المنسقة وقنواتها المائية ونقاط المشاهدة القلعة ومآذن القاهرة التاريخية.",
                kids = "حديقة كبيرة فوق تل يمكنك أن تجري فيها على العشب وترى المدينة القديمة كلها بقبابها ومآذنها.",
                lookFor = listOf("أفق المآذن وقت الغروب", "السور الأيوبي المرمم على حافة الحديقة", "القنوات المائية المستوحاة من تصميم الحدائق الإسلامية"),
                tips = "وقت الغروب هو الأكثر شعبية. مكان هادئ ومناسب للاستراحة بين المعالم المزدحمة.",
            ),
        ),
        CatalogPlace(
            id = "coptic_cairo", destinationIds = CG, nameEn = "Coptic Cairo & the Hanging Church", nameAr = "مصر القديمة والكنيسة المعلقة", area = "old_cairo",
            lat = 30.0053, lng = 31.2302, interests = setOf(HISTORY, ART), category = StopCategory.SIGHT,
            typicalDurationMinutes = 90, minDurationMinutes = 60, priority = 2, typicalOpens = h(9), typicalCloses = h(16),
            priceEstimate = null, motif = ArtMotif.CHURCH, walkingIntensity = WalkingLevel.MODERATE,
            guideEn = LocalizedGuide(
                short = "A historic quarter of Old Cairo with ancient churches, a synagogue and the Coptic Museum, set within the remains of the Roman Babylon Fortress.",
                detailed = "The Hanging Church (al-Muallaqa) earned its name because it was built on top of a gatehouse of the Roman-era Babylon Fortress, so its nave seems suspended above the passage below. Nearby lanes lead to other old churches, the Ben Ezra Synagogue and the Coptic Museum, which displays Christian art from Egypt.",
                kids = "This church was built on top of an old Roman gate, so it is 'hanging' in the air! Look for the wooden ceiling shaped like the inside of a boat.",
                lookFor = listOf("The ribbed wooden roof of the Hanging Church", "Remains of the Roman fortress towers", "Icons and carved wooden screens"),
                tips = "These are active places of worship: dress modestly, keep voices low and follow photography rules posted at each site.",
            ),
            guideAr = LocalizedGuide(
                short = "حي تاريخي في مصر القديمة يضم كنائس عريقة ومعبدًا يهوديًا والمتحف القبطي، داخل بقايا حصن بابليون الروماني.",
                detailed = "سُميت الكنيسة المعلقة بهذا الاسم لأنها بُنيت فوق بوابة من حصن بابليون الروماني، فيبدو صحنها معلقًا فوق الممر أسفله. وتقود الأزقة القريبة إلى كنائس قديمة أخرى ومعبد بن عزرا والمتحف القبطي الذي يعرض الفن المسيحي في مصر.",
                kids = "بُنيت هذه الكنيسة فوق بوابة رومانية قديمة، لذلك هي 'معلقة' في الهواء! ابحث عن السقف الخشبي الذي يشبه باطن سفينة.",
                lookFor = listOf("السقف الخشبي المضلع للكنيسة المعلقة", "بقايا أبراج الحصن الروماني", "الأيقونات والأحجبة الخشبية المنحوتة"),
                tips = "هذه دور عبادة عاملة: ارتدِ ملابس محتشمة، واخفض صوتك، والتزم بقواعد التصوير المعلنة في كل موقع.",
            ),
        ),
        CatalogPlace(
            id = "nmec", destinationIds = CG, nameEn = "National Museum of Egyptian Civilization", nameAr = "المتحف القومي للحضارة المصرية", area = "old_cairo",
            lat = 30.0081, lng = 31.2480, interests = setOf(MUSEUMS, HISTORY, FAMILY), category = StopCategory.SIGHT,
            typicalDurationMinutes = 120, minDurationMinutes = 75, priority = 2, typicalOpens = h(9), typicalCloses = h(17),
            priceEstimate = 500.0, motif = ArtMotif.MUSEUM, walkingIntensity = WalkingLevel.LIMITED,
            guideEn = LocalizedGuide(
                short = "A museum in Fustat tracing Egyptian civilisation from prehistory to today, home since 2021 to the royal mummies.",
                detailed = "In April 2021, twenty-two royal mummies were moved here from the Egyptian Museum in a televised procession known as the Pharaohs' Golden Parade. The Royal Mummies Hall is presented in a dimly lit, respectful setting, while the main gallery covers crafts, writing, beliefs and daily life across thousands of years.",
                kids = "Here you can see real ancient kings and queens, carefully preserved. It's quiet and dark in that hall, so whisper like an explorer!",
                lookFor = listOf("The Royal Mummies Hall", "The overview gallery showing how crafts changed over time", "The views over Ain al-Sira lake"),
                tips = "Photography rules differ in the mummies hall. The museum is well suited to visitors who prefer less walking.",
            ),
            guideAr = LocalizedGuide(
                short = "متحف في الفسطاط يتتبع الحضارة المصرية من عصور ما قبل التاريخ حتى اليوم، ويضم منذ 2021 المومياوات الملكية.",
                detailed = "في أبريل 2021 نُقلت اثنتان وعشرون مومياء ملكية إلى هنا من المتحف المصري في موكب متلفز عُرف باسم موكب المومياوات الملكية. تُعرض قاعة المومياوات الملكية في إضاءة خافتة وأجواء تحترم المكان، بينما تغطي القاعة الرئيسية الحرف والكتابة والمعتقدات والحياة اليومية عبر آلاف السنين.",
                kids = "هنا يمكنك رؤية ملوك وملكات حقيقيين من مصر القديمة محفوظين بعناية. القاعة هادئة ومظلمة، فتحدث همسًا مثل المستكشفين!",
                lookFor = listOf("قاعة المومياوات الملكية", "القاعة العامة التي توضح تطور الحرف عبر الزمن", "الإطلالة على بحيرة عين الصيرة"),
                tips = "تختلف قواعد التصوير في قاعة المومياوات. المتحف مناسب للزوار الذين يفضلون مشيًا أقل.",
            ),
        ),
        CatalogPlace(
            id = "cairo_tower", destinationIds = CG, nameEn = "Cairo Tower", nameAr = "برج القاهرة", area = "zamalek",
            lat = 30.0459, lng = 31.2243, interests = setOf(PHOTOGRAPHY, FAMILY), category = StopCategory.SIGHT,
            typicalDurationMinutes = 45, minDurationMinutes = 30, priority = 1, typicalOpens = h(9), typicalCloses = h(23, 30),
            priceEstimate = 350.0, motif = ArtMotif.TOWER, walkingIntensity = WalkingLevel.LIMITED,
            guideEn = LocalizedGuide(
                short = "A slender tower completed in 1961 on Gezira Island, with a lattice design inspired by the lotus plant and a 360° viewing deck.",
                detailed = "Rising roughly 187 metres above Zamalek, Cairo Tower was for years the tallest structure in the city. Its concrete lattice evokes a lotus, a classic symbol in Egyptian art. On a clear day the deck shows the Nile bridges, downtown and, on the horizon, sometimes the Giza pyramids.",
                kids = "Ride the lift to the top of a tower shaped like a giant flower and see the river Nile from high above!",
                lookFor = listOf("The Nile bridges and islands", "The lotus-shaped lattice from below", "The pyramids on the horizon on very clear days"),
                tips = "Visibility varies with haze; sunset and evening are popular. Lines can form for the lift.",
            ),
            guideAr = LocalizedGuide(
                short = "برج رشيق اكتمل عام 1961 في جزيرة الجزيرة، بتصميم شبكي مستوحى من زهرة اللوتس ومنصة مشاهدة بزاوية 360 درجة.",
                detailed = "يرتفع برج القاهرة نحو 187 مترًا فوق الزمالك، وكان لسنوات أطول مبنى في المدينة. يستحضر هيكله الخرساني الشبكي زهرة اللوتس، الرمز الكلاسيكي في الفن المصري. وفي الأيام الصافية تظهر من المنصة جسور النيل ووسط البلد، وأحيانًا أهرامات الجيزة في الأفق.",
                kids = "اركب المصعد إلى قمة برج على شكل زهرة عملاقة وشاهد نهر النيل من الأعلى!",
                lookFor = listOf("جسور النيل وجزره", "الهيكل الشبكي على شكل اللوتس من الأسفل", "الأهرامات في الأفق في الأيام شديدة الصفاء"),
                tips = "تختلف الرؤية بحسب الشبورة؛ الغروب والمساء من الأوقات المفضلة. قد يكون هناك طابور للمصعد.",
            ),
        ),
        CatalogPlace(
            id = "nile_felucca", destinationIds = CG, nameEn = "Felucca ride on the Nile", nameAr = "رحلة فلوكة في النيل", area = "downtown",
            lat = 30.0400, lng = 31.2290, interests = setOf(NATURE, PHOTOGRAPHY, FAMILY), category = StopCategory.SIGHT,
            typicalDurationMinutes = 60, minDurationMinutes = 45, priority = 1, typicalOpens = null, typicalCloses = null,
            priceEstimate = 300.0, motif = ArtMotif.NILE, walkingIntensity = WalkingLevel.LIMITED,
            guideEn = LocalizedGuide(
                short = "A gentle sail on a traditional wooden felucca — one of the most relaxing ways to see Cairo from the Nile.",
                detailed = "Feluccas are traditional sailing boats that have long been used along the Nile. In Cairo, short rides leave from landings along the Corniche and around Garden City and Zamalek. With little engine noise, you get a calm view of bridges, palm-lined banks and the city skyline.",
                kids = "Sail on a boat that moves with the wind — no engine! Watch the big triangle sail catch the breeze.",
                lookFor = listOf("The skyline at golden hour", "Herons and egrets along the banks", "The large triangular lateen sail"),
                tips = "Agree price and duration before boarding, ask for life jackets for children, and bring a light layer — it can be breezy on the water.",
            ),
            guideAr = LocalizedGuide(
                short = "إبحار هادئ على فلوكة خشبية تقليدية — من أكثر الطرق راحةً لمشاهدة القاهرة من النيل.",
                detailed = "الفلوكة قارب شراعي تقليدي استُخدم طويلًا على امتداد النيل. وفي القاهرة تنطلق الرحلات القصيرة من مراسٍ على الكورنيش وحول جاردن سيتي والزمالك. ومع غياب ضجيج المحركات تقريبًا، تستمتع بمنظر هادئ للجسور والضفاف المزروعة بالنخيل وأفق المدينة.",
                kids = "أبحر في قارب يتحرك بالرياح — بدون محرك! شاهد الشراع المثلث الكبير وهو يمتلئ بالهواء.",
                lookFor = listOf("أفق المدينة وقت الغروب", "طيور البلشون والبلشون الأبيض على الضفاف", "الشراع المثلث الكبير"),
                tips = "اتفق على السعر والمدة قبل الصعود، واطلب سترات نجاة للأطفال، واحمل طبقة خفيفة فالجو قد يكون منعشًا على الماء.",
            ),
        ),
        CatalogPlace(
            id = "bibliotheca", destinationIds = ALX, nameEn = "Bibliotheca Alexandrina", nameAr = "مكتبة الإسكندرية", area = "central",
            lat = 31.2089, lng = 29.9092, interests = setOf(MUSEUMS, ART, HISTORY), category = StopCategory.SIGHT,
            typicalDurationMinutes = 120, minDurationMinutes = 60, priority = 3, typicalOpens = h(10), typicalCloses = h(19),
            priceEstimate = 300.0, motif = ArtMotif.LIBRARY, walkingIntensity = WalkingLevel.LIMITED,
            guideEn = LocalizedGuide(
                short = "A striking modern library and cultural centre opened in 2002, created in memory of the ancient Library of Alexandria.",
                detailed = "The main building is a huge tilted disc facing the sea, with a terraced reading room under a roof that lets in filtered daylight. Its granite outer wall is carved with characters from many of the world's scripts. Inside are museums, exhibitions and a planetarium.",
                kids = "This library looks like a giant sun rising from the ground! Its outside wall has letters from alphabets all over the world — can you find one you know?",
                lookFor = listOf("The granite wall carved with world scripts", "The terraced main reading room", "The antiquities and manuscripts museums"),
                tips = "Some areas have separate tickets or guided-visit rules; bags may need to be checked at the entrance.",
            ),
            guideAr = LocalizedGuide(
                short = "مكتبة ومركز ثقافي حديث ولافت افتُتح عام 2002، أُنشئ إحياءً لذكرى مكتبة الإسكندرية القديمة.",
                detailed = "المبنى الرئيسي قرص ضخم مائل يواجه البحر، وتحته قاعة قراءة متدرجة تحت سقف يُدخل ضوء النهار بشكل ناعم. وجداره الخارجي من الجرانيت منقوش بحروف من كثير من أنظمة الكتابة في العالم. وفي الداخل متاحف ومعارض وقبة سماوية.",
                kids = "هذه المكتبة تشبه شمسًا عملاقة تشرق من الأرض! جدارها الخارجي فيه حروف من أبجديات العالم — هل تجد حرفًا تعرفه؟",
                lookFor = listOf("الجدار الجرانيتي المنقوش بكتابات العالم", "قاعة القراءة الرئيسية المتدرجة", "متحفا الآثار والمخطوطات"),
                tips = "بعض الأقسام لها تذاكر منفصلة أو قواعد للزيارة المصحوبة بمرشد، وقد يُطلب إيداع الحقائب عند المدخل.",
            ),
        ),
        CatalogPlace(
            id = "qaitbay", destinationIds = ALX, nameEn = "Citadel of Qaitbay", nameAr = "قلعة قايتباي", area = "western_harbour",
            lat = 31.2140, lng = 29.8856, interests = setOf(HISTORY, PHOTOGRAPHY, FAMILY), category = StopCategory.SIGHT,
            typicalDurationMinutes = 75, minDurationMinutes = 45, priority = 3, typicalOpens = h(9), typicalCloses = h(17),
            priceEstimate = 200.0, motif = ArtMotif.FORT, walkingIntensity = WalkingLevel.MODERATE,
            guideEn = LocalizedGuide(
                short = "A 15th-century sea fortress built by the Mamluk sultan Qaitbay on the site of the ancient Lighthouse of Alexandria.",
                detailed = "Sultan al-Ashraf Qaitbay built the fortress in the 1470s to defend the harbour. It stands where the Pharos — the famous lighthouse counted among the Seven Wonders of the Ancient World — once stood, and some stone from the lighthouse ruins is believed to have been reused in its walls.",
                kids = "A real sea castle with thick walls and lookout windows, built where one of the most famous lighthouses in history used to shine!",
                lookFor = listOf("Sea views through the defensive windows", "The thick granite and limestone walls", "Fishing boats in the Eastern Harbour"),
                tips = "It can be windy by the sea. Stairs and uneven floors inside — wear good shoes.",
            ),
            guideAr = LocalizedGuide(
                short = "قلعة بحرية من القرن الخامس عشر بناها السلطان المملوكي قايتباي في موقع فنار الإسكندرية القديم.",
                detailed = "بنى السلطان الأشرف قايتباي القلعة في سبعينيات القرن الخامس عشر لحماية الميناء. وتقف في الموقع الذي كان فيه فنار الإسكندرية — أحد عجائب الدنيا السبع القديمة — ويُعتقد أن بعض أحجار أطلال الفنار أُعيد استخدامها في أسوارها.",
                kids = "قلعة بحرية حقيقية بأسوار سميكة ونوافذ للمراقبة، بُنيت في مكان كان يضيء فيه واحد من أشهر المنارات في التاريخ!",
                lookFor = listOf("إطلالات البحر عبر النوافذ الدفاعية", "الأسوار السميكة من الجرانيت والحجر الجيري", "قوارب الصيد في الميناء الشرقي"),
                tips = "قد يكون الجو عاصفًا قرب البحر. توجد سلالم وأرضيات غير مستوية بالداخل — ارتدِ حذاءً مناسبًا.",
            ),
        ),
        CatalogPlace(
            id = "kom_shoqafa", destinationIds = ALX, nameEn = "Catacombs of Kom el Shoqafa", nameAr = "مقابر كوم الشقافة", area = "karmouz",
            lat = 31.1786, lng = 29.8930, interests = setOf(HISTORY, ART), category = StopCategory.SIGHT,
            typicalDurationMinutes = 60, minDurationMinutes = 40, priority = 2, typicalOpens = h(9), typicalCloses = h(17),
            priceEstimate = 150.0, motif = ArtMotif.MUSEUM, walkingIntensity = WalkingLevel.ACTIVE,
            guideEn = LocalizedGuide(
                short = "A Roman-era underground necropolis whose carvings blend Egyptian, Greek and Roman artistic styles.",
                detailed = "The catacombs were cut into the rock on several levels, reached by a spiral staircase around a central shaft. The main tomb's decoration mixes Egyptian gods and symbols with Greco-Roman clothing and architecture — a vivid picture of Alexandria's mixed culture. A popular local story says the site was rediscovered in 1900 when a donkey fell through the ground.",
                kids = "Go underground into a secret city of tombs! Look for Egyptian gods wearing Roman soldier outfits.",
                lookFor = listOf("The spiral staircase around the light shaft", "Anubis depicted in Roman armour", "The banquet hall carved into the rock"),
                tips = "Steps and low light underground; not ideal for limited mobility. Lower levels can sometimes be closed due to groundwater.",
            ),
            guideAr = LocalizedGuide(
                short = "جبانة تحت الأرض من العصر الروماني تمزج نقوشها بين الأساليب الفنية المصرية واليونانية والرومانية.",
                detailed = "نُحتت المقابر في الصخر على عدة مستويات يُنزل إليها عبر درج حلزوني حول بئر مركزية. وتمزج زخارف المقبرة الرئيسية بين الآلهة والرموز المصرية والملابس والعمارة اليونانية الرومانية — صورة حية لثقافة الإسكندرية المختلطة. وتقول حكاية محلية شائعة إن الموقع اكتُشف من جديد عام 1900 عندما سقط حمار في حفرة بالأرض.",
                kids = "انزل تحت الأرض إلى مدينة سرية من المقابر! ابحث عن آلهة مصرية ترتدي ملابس جنود رومان.",
                lookFor = listOf("الدرج الحلزوني حول بئر الضوء", "أنوبيس مصوَّرًا بدرع روماني", "قاعة الولائم المنحوتة في الصخر"),
                tips = "سلالم وإضاءة خافتة تحت الأرض؛ غير مثالية لمحدودي الحركة. قد تُغلق المستويات السفلى أحيانًا بسبب المياه الجوفية.",
            ),
        ),
        CatalogPlace(
            id = "alex_museum", destinationIds = ALX, nameEn = "Alexandria National Museum", nameAr = "متحف الإسكندرية القومي", area = "central",
            lat = 31.2007, lng = 29.9132, interests = setOf(MUSEUMS, HISTORY), category = StopCategory.SIGHT,
            typicalDurationMinutes = 75, minDurationMinutes = 45, priority = 2, typicalOpens = h(9), typicalCloses = h(17),
            priceEstimate = 150.0, motif = ArtMotif.MUSEUM, walkingIntensity = WalkingLevel.LIMITED,
            guideEn = LocalizedGuide(
                short = "A compact museum in a restored Italian-style palace, telling Alexandria's story from Pharaonic times through the Greco-Roman, Coptic and Islamic eras.",
                detailed = "Because the collection is arranged chronologically over a few floors, it is an efficient introduction to the city's layered history, including objects recovered from underwater archaeology in the harbour.",
                kids = "A small palace full of treasures — some of them were found under the sea!",
                lookFor = listOf("Finds from underwater archaeology", "Greco-Roman sculpture", "The building's own architecture"),
                tips = "A good first stop to understand the city before visiting other sites.",
            ),
            guideAr = LocalizedGuide(
                short = "متحف متوسط الحجم في قصر مرمم على الطراز الإيطالي، يروي قصة الإسكندرية من العصر الفرعوني مرورًا بالعصور اليونانية الرومانية والقبطية والإسلامية.",
                detailed = "لأن المجموعة مرتبة زمنيًا على عدة طوابق، فهو مدخل سريع ومفيد لتاريخ المدينة متعدد الطبقات، ويضم قطعًا انتُشلت من أعمال الآثار الغارقة في الميناء.",
                kids = "قصر صغير مليء بالكنوز — بعضها وُجد تحت البحر!",
                lookFor = listOf("قطع من الآثار الغارقة", "منحوتات يونانية رومانية", "عمارة المبنى نفسه"),
                tips = "محطة أولى جيدة لفهم المدينة قبل زيارة باقي المواقع.",
            ),
        ),
        CatalogPlace(
            id = "montaza", destinationIds = ALX, nameEn = "Montaza Palace Gardens", nameAr = "حدائق قصر المنتزه", area = "montaza",
            lat = 31.2886, lng = 30.0160, interests = setOf(NATURE, FAMILY, PHOTOGRAPHY), category = StopCategory.SIGHT,
            typicalDurationMinutes = 90, minDurationMinutes = 45, priority = 1, typicalOpens = h(8), typicalCloses = h(22),
            priceEstimate = 50.0, motif = ArtMotif.GARDEN, walkingIntensity = WalkingLevel.MODERATE,
            guideEn = LocalizedGuide(
                short = "Expansive seaside gardens around palaces built for Egypt's royal family in the late 19th and early 20th centuries.",
                detailed = "The gardens stretch along a private bay at Alexandria's eastern edge. The palace buildings mix several European and local styles; access to their interiors is often restricted, so plan to enjoy the gardens, beaches and sea views.",
                kids = "Huge gardens by the sea with palm trees and a palace that looks like it came from a story book.",
                lookFor = listOf("The palace's tower from the gardens", "The small bay and bridge", "Old trees and palm avenues"),
                tips = "It is far east of the centre — allow travel time. Evenings and weekends can be busy with local families.",
            ),
            guideAr = LocalizedGuide(
                short = "حدائق واسعة على البحر حول قصور بُنيت للأسرة المالكة المصرية في أواخر القرن التاسع عشر وأوائل القرن العشرين.",
                detailed = "تمتد الحدائق على خليج خاص في الطرف الشرقي للإسكندرية. وتمزج مباني القصر بين عدة طُرز أوروبية ومحلية، وغالبًا ما يكون دخولها مقيدًا، لذا خطط للاستمتاع بالحدائق والشواطئ وإطلالات البحر.",
                kids = "حدائق ضخمة على البحر فيها نخيل وقصر يبدو وكأنه خرج من كتاب حكايات.",
                lookFor = listOf("برج القصر من الحدائق", "الخليج الصغير والجسر", "الأشجار القديمة وممرات النخيل"),
                tips = "تقع بعيدًا شرق وسط المدينة — احسب وقت الانتقال. قد تزدحم مساءً وفي العطلات بالعائلات.",
            ),
        ),
        CatalogPlace(
            id = "corniche_walk", destinationIds = ALX, nameEn = "Corniche seafront walk", nameAr = "التمشية على الكورنيش", area = "central",
            lat = 31.2050, lng = 29.9050, interests = setOf(NATURE, PHOTOGRAPHY, FOOD), category = StopCategory.SIGHT,
            typicalDurationMinutes = 60, minDurationMinutes = 30, priority = 1, typicalOpens = null, typicalCloses = null,
            priceEstimate = null, motif = ArtMotif.SEA, walkingIntensity = WalkingLevel.MODERATE,
            guideEn = LocalizedGuide(
                short = "Alexandria's long Mediterranean promenade curving around the Eastern Harbour — the best way to feel the city's sea air.",
                detailed = "The Corniche links many of the city's landmarks, from the area near the Bibliotheca to Qaitbay at the harbour's western tip. Along the way you pass cafés, fish restaurants and early 20th-century façades.",
                kids = "Walk by the sea, watch the fishing boats and feel the wind!",
                lookFor = listOf("The curve of the Eastern Harbour", "Fishing boats at the harbour", "Historic façades facing the sea"),
                tips = "Traffic is heavy: cross only at designated crossings. Sunset is the classic time.",
            ),
            guideAr = LocalizedGuide(
                short = "ممشى الإسكندرية الطويل على البحر المتوسط حول الميناء الشرقي — أفضل طريقة للإحساس بنسيم المدينة.",
                detailed = "يربط الكورنيش كثيرًا من معالم المدينة، من المنطقة القريبة من المكتبة حتى قلعة قايتباي عند الطرف الغربي للميناء. وتمر في طريقك بمقاهٍ ومطاعم أسماك وواجهات من أوائل القرن العشرين.",
                kids = "تمشَّ بجوار البحر، وشاهد قوارب الصيد، واشعر بالهواء!",
                lookFor = listOf("انحناءة الميناء الشرقي", "قوارب الصيد في الميناء", "الواجهات التاريخية المطلة على البحر"),
                tips = "المرور كثيف: اعبر فقط من الأماكن المخصصة. الغروب هو الوقت الكلاسيكي.",
            ),
        ),
    )

    /** City-level etiquette text used for the "local customs" quick question offline. */
    fun customs(ar: Boolean): String = if (ar)
        "في مصر، يُقدَّر اللباس المحتشم عند زيارة المساجد والكنائس (تغطية الكتفين والركبتين، وغطاء رأس للنساء في المساجد). البقشيش شائع مقابل الخدمات الصغيرة. استأذن قبل تصوير الأشخاص، وتجنب الأكل أو الشرب علنًا نهارًا في رمضان احترامًا للصائمين. التحية الودودة تفتح الأبواب: «السلام عليكم» أو «صباح الخير»."
    else
        "In Egypt, modest dress is appreciated when visiting mosques and churches (shoulders and knees covered; women cover their hair inside mosques). Tipping (baksheesh) for small services is common. Ask before photographing people, and during Ramadan avoid eating or drinking openly in daytime out of respect. A friendly greeting goes a long way: \"As-salamu alaykum\" or \"Sabah el-kheir\"."
}
