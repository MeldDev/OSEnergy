"""Russian explanatory names where this modpack has no usable translation."""
import re

EXACT = dict(line.split('=', 1) for line in '''
TNT Run=Блок игры «Бег по динамиту»
Spleef=Блок игры «Сплиф»
Speech Box=Речевой синтезатор
Portable Tape Drive=Переносной кассетный привод
Sound Card=Звуковая карта
Light Board=Световая плата
Server Self-Destructor=Самоуничтожитель сервера
Rack Capacitor=Конденсатор серверной стойки
Switch Board=Плата переключения
Speech Upgrade=Речевое улучшение
Magical Memory (Creative)=Магическая память (творческий режим)
Light Node=Световой узел
Limited Item Filter=Ограничивающий фильтр предметов
Staff of Teleportation=Посох телепортации
"Enderios"=Эндер-хлопья
RAID=Дисковый массив
EEPROM=Микросхема памяти
Crecopter=Крекоптер
Smithing Table=Кузнечный стол
Composter=Компостер
Stonecutter=Камнерез
Fletching Table=Стол лучника
Cartography Table=Стол картографа
Loom=Ткацкий станок
QED=Квантовый преобразователь
Debug Frame Motor=Отладочный рамочный двигатель
Terminal Glasses Bridge=Мост терминальных очков
PIM=Менеджер инвентаря игрока
Sensor=Датчик
Item Selector=Селектор предметов
Terminal Glasses=Терминальные очки
Wireless Keyboard=Беспроводная клавиатура
Duck Antenna=Антенна-утка
Magnetic Card Reader=Считыватель магнитных карт
RFID Reader=Считыватель радиочастотных карт
Card Writer=Устройство записи карт
Alarm=Сигнализация
Entity Detector=Детектор сущностей
Door Controller=Контроллер двери
Data Block=Блок данных
Switchable Hub=Управляемый сетевой концентратор
KVM Switch=Переключатель клавиатуры, видео и мыши
Energy Turret=Энергетическая турель
Keypad=Кодовая панель
Biometric Reader=Биометрический сканер
Magnetic Strip Card=Магнитная карта
RFID Card=Радиочастотная карта
RFID Reader Card=Карта считывания радиочастотных меток
Secure Network Card=Защищённая сетевая карта
Security Door=Защитная дверь
Private Security Door=Личная защитная дверь
Damage Upgrade=Улучшение урона
Cooldown Upgrade=Улучшение перезарядки
Energy Upgrade=Энергетическое улучшение
Movement Upgrade=Улучшение движения
Scripted Item=Скриптовый предмет
/dev/null=Утилизатор предметов
Ztone Tile=Декоративная плитка
Garden Soil=Садовая почва
Booster=Ускоритель
Flat Lamp=Плоская лампа
Aurora=Аврора
MSG Pill=Таблетка глутамата
Mini Charcoal=Маленький древесный уголь
Mini Coal=Маленький уголь
Ofanix=Офаникс
Diamond Zane=Алмазный Зейн
Splat Axe=Топор «Сплэт»
Terrain Eater=Пожиратель ландшафта
Totem Tool=Инструмент-тотем
'''.strip().splitlines())

BASE = dict(line.split('=', 1) for line in '''
aim_point_block=Блок мишени
blockDynamite=Установленный динамит
blockDynamiteRemote=Установленный дистанционный динамит
plasmaLauncher=Плазменная пушка
itemDebug=Отладочный инструмент
itemCropSeed=Семена сельскохозяйственной культуры
itemBarrel=Бочка
itemMugBooze=Кружка алкогольного напитка
BlockMolecularTransformer=Молекулярный преобразователь
tile.BlockPaint=Краска в мире
item.ItemAEStackDisplay=Служебное отображение стопки предметов
item.ItemVirtualCrafting=Служебный предмет виртуального создания
ceramic=Керамика
stained=Окрашенная керамика
ceramicPattern=Узорная керамика
ceramicBrick=Керамический кирпич
database=База данных
databaseMoth=База данных бабочек
soilMeter=Измеритель почвы
planks=Доски
multifence=Составной забор
gate=Калитка
door=Дверь
stairs=Ступени
carpentry=Столярный блок
panel=Деревянная панель
hammer=Молоток
durableHammer=Прочный молоток
buildToolBlock=Служебный блок строительного инструмента
blockRedPlasma=Красная плазма
pipeBlock=Блок трубы
pipeGate=Вентиль трубы
pipePlug=Заглушка трубы
pipePowerAdapter=Энергетический адаптер трубы
sandstone2=Декоративный песчаник
chisel=Железное долото
diamondChisel=Алмазное долото
obsidianChisel=Обсидиановое долото
blockInhibitorObelisk=Обелиск подавления
placedItem=Размещённый предмет
safetyFlame=Безопасное пламя
chaosShardAtmos=Атмосферный осколок хаоса
tclogo=Логотип мода
adminSpawnEgg=Администраторское яйцо призыва
draconiumFluxCapacitor=Дракониевый флаксовый конденсатор
opFrame=Рамка изображения
opFrameAnim=Анимированная рамка изображения
LogoDw=Логотип города гномов
Ceramic=Керамика
BankomatFront=Передняя панель банкомата
BankomatSide=Боковая панель банкомата
CaseExchangerFront=Передняя панель обменника кейсов
CaseExchangerSide=Боковая панель обменника кейсов
Sun_materia=Солнечная материя
Cpu3=Видеокарта «ДжиФорс 1060»
Resize_Potion=Зелье изменения размера
EarthArmorHead=Шлем земной брони
EarthArmorChest=Нагрудник земной брони
EarthArmorLegs=Поножи земной брони
EarthArmorBoots=Ботинки земной брони
SnowPhen=Декоративный снег
datablock=Блок данных
relocation.blockmovingrow=Движущийся ряд блоков
SecurityDoor=Защитная дверь
SecurityDoorPrivate=Личная защитная дверь
genesis=Генезис
fieldKit=Полевой набор
hiveFrame.cocoa=Какаовая рамка
hiveFrame.cage=Рамка-клетка
hiveFrame.soul=Рамка души
hiveFrame.clay=Глиняная рамка
hiveFrame.debug=Отладочная рамка
dictionary=Справочник пчеловода
serumArray=Набор генетических сывороток
serum=Генетическая сыворотка
sequence=Генетическая последовательность
analyst=Генетический анализатор
registry=Генетический реестр
masterRegistry=Главный генетический реестр
robotStation=Станция робота
lexicon=Лексикон преобразований
igniter=Поджигатель
chiller=Охладитель
pump=Насос
transfuser=Переносчик энергии
infinityBoosterIcon=Служебный значок бесконечного усиления
boosterBG=Служебный фон усилителя
beverage=Напиток
'''.strip().splitlines())

MATERIALS = dict(line.split('=', 1) for line in '''
Peat=торф
Apatite=апатит
Fertilizer=удобрение
Mulch=мульча
Phosphor=фосфор
Ash=пепел
Tin=олово
Copper=медь
Bronze=бронза
Humus=гумус
Bogearth=болотная земля
Wheat=пшеница
Cookies=печенье
Redstone=красная пыль
Lapis=лазурит
Reeds=сахарный тростник
Clay=глина
Glowstone=светокамень
Apples=яблоки
Netherwart=адский нарост
Charcoal=древесный уголь
Coal=уголь
Seeds=семена
Potatoes=картофель
Carrots=морковь
Wood=дубовая древесина
SpruceWood=еловая древесина
BirchWood=берёзовая древесина
JungleWood=тропическая древесина
AcaciaWood=древесина акации
DarkOakWood=древесина тёмного дуба
Cobblestone=булыжник
Dirt=земля
Podzol=подзол
Stone=камень
Brick=кирпич
Cacti=кактус
Sand=песок
RedSand=красный песок
Obsidian=обсидиан
Netherrack=адский камень
Soulsand=песок душ
Sandstone=песчаник
Netherbrick=адский кирпич
Mycelium=мицелий
Gravel=гравий
Saplings=саженцы дуба
SpruceSapling=саженцы ели
BirchSapling=саженцы берёзы
JungleSapling=тропические саженцы
AcaciaSapling=саженцы акации
DarkOakSapling=саженцы тёмного дуба
Beeswax=пчелиный воск
Pollen=пыльца
CrystallinePollen=кристаллическая пыльца
Propolis=прополис
Honeydew=медовая роса
RoyalJelly=маточное молочко
Honeycombs=медовые соты
CocoaComb=какаовые соты
SimmeringCombs=кипящие соты
StringyCombs=волокнистые соты
FrozenCombs=мёрзлые соты
DrippingCombs=капающие соты
SilkyCombs=шелковистые соты
ParchedCombs=иссушенные соты
MysteriousCombs=загадочные соты
PowderyCombs=порошкообразные соты
WheatenCombs=пшеничные соты
MossyCombs=мшистые соты
MellowCombs=мягкие соты
RefractoryWax=огнеупорный воск
Cherry=вишня
Walnut=грецкие орехи
Chestnut=каштаны
Lemon=лимоны
Plum=сливы
Papaya=папайя
Dates=финики
Resin=смола
Rubber=резина
Scrap=утильсырьё
Silver=серебро
Brass=латунь
'''.strip().splitlines())

WORDS = dict(zip(
    'log planks oak spruce birch jungle acacia big stone stonebrick slab stair rope gravel sand sandstone carved smooth obsidian end blackstone basalt cobblestone mossy cracked brick netherrack quartz block chiseled pillar glass silver magenta orange lime cyan red yellow green blue pink purple black white gray brown light bedrock cobbled deepslate prismarine purpur nether coal iron gold redstone lapis diamond emerald misc grid cherry frozen snow ice'.split(),
    'бревно доски дуб ель берёза тропический акация большой камень каменный-кирпич плита ступени верёвка гравий песок песчаник резной гладкий обсидиан Край чернокамень базальт булыжник замшелый потрескавшийся кирпич адский-камень кварц блок резной колонна стекло серебристый пурпурный оранжевый лаймовый бирюзовый красный жёлтый зелёный синий розовый фиолетовый чёрный белый серый коричневый светлый бедрок колотый глубинный-сланец призмарин пурпур адский уголь железо золото редстоун лазурит алмаз изумруд декоративный решётка вишня замёрзший снег лёд'.split()))

def transliterate(s):
    for a,b in [('sh','ш'),('ch','ч'),('th','т'),('ph','ф'),('qu','кв'),('oo','у'),('ee','и')]:
        s=s.lower().replace(a,b)
    return s.translate(str.maketrans(dict(zip('abcdefghijklmnopqrstuvwxyz','абкдефгхийклмнопкрстуввксйз'))))

def explanatory(key, label):
    mod, base = key.split('@')[0].split(':',1)
    meta = key.split('@')[1] if '@' in key else None
    if label in EXACT:
        return EXACT[label]
    if base in BASE:
        return BASE[base] + (f' (вариант {meta})' if meta else '')
    if mod == 'Ztones' and base == 'tile.glaxx':
        return f'Декоративное стекло «Глакс» (вариант {meta})'
    if mod == 'Ztones' and re.match(r'tile\..+Block$',base):
        return 'Декоративный блок «'+transliterate(base[5:-5]).capitalize()+f'» (вариант {meta})'
    if mod == 'Forestry' and base.startswith('crated'):
        return 'Ящик: '+MATERIALS[base[6:]]
    if mod == 'Forestry' and base == 'factory':
        return f'Заводской блок (вариант {meta}; название в сборке отсутствует)'
    if mod == 'etfuturum' and base.endswith('_bed'):
        color=base[:-4]
        colors={'white':'Белая','orange':'Оранжевая','magenta':'Пурпурная','light_blue':'Голубая','yellow':'Жёлтая','lime':'Лаймовая','pink':'Розовая','gray':'Серая','light_gray':'Светло-серая','cyan':'Бирюзовая','purple':'Фиолетовая','blue':'Синяя','brown':'Коричневая','green':'Зелёная','black':'Чёрная'}
        return colors[color]+' кровать'
    if mod == 'dwcity':
        if re.match(r'(Powder|Book)\d+$',base):
            return ('Порошок' if base.startswith('Powder') else 'Книга')+' № '+re.search(r'\d+',base)[0]+' (название в сборке отсутствует)'
        tokens=re.findall(r'[A-Za-z]+|\d+',base)
        if all(t.lower() in WORDS or t.isdigit() for t in tokens):
            return 'Декор: '+' / '.join(WORDS.get(t.lower(),t).replace('-',' ') for t in tokens)+(f' (вариант {meta})' if meta else '')
    return 'Предмет без перевода «'+base+'»'
