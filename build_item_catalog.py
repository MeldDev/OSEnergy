from pathlib import Path
import collections
import json
import re
import zipfile
from item_catalog_translations import explanatory

ROOT = Path(r'C:\Users\DESKTOP\McSkill\clients\Industrial_1.7.10')
OUT = Path(__file__).parent
CYR = re.compile(r'[А-Яа-яЁё]')
ru, en = {}, {}
archives = []
for folder in ('mods', 'nestedmods', 'resourcepacks'):
    archives.extend(sorted((ROOT / folder).rglob('*.jar')))
    archives.extend(sorted((ROOT / folder).rglob('*.zip')))

def read_lang(data):
    result = {}
    for line in data.decode('utf-8-sig', errors='replace').splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            result[key.strip()] = value.strip()
    return result

inventory = []
for path in archives:
    try:
        with zipfile.ZipFile(path) as z:
            langs = [n for n in z.namelist() if re.search(r'/lang/(ru_ru|en_us)\.(lang|properties)$', n, re.I)]
            inventory.append((str(path.relative_to(ROOT)), len(langs)))
            for n in langs:
                (ru if 'ru_ru' in n.lower() else en).update(read_lang(z.read(n)))
    except zipfile.BadZipFile:
        inventory.append((str(path.relative_to(ROOT)), -1))
for path in sorted((ROOT / 'config/txloader').rglob('*.lang')):
    if path.name.lower() in ('ru_ru.lang', 'en_us.lang'):
        (ru if path.name.lower() == 'ru_ru.lang' else en).update(read_lang(path.read_bytes()))

english_to_ru = {value: ru[key] for key, value in en.items() if key in ru and CYR.search(ru[key])}
source = ROOT / 'config/AppliedEnergistics2/recipes/items.csv'
rows = {}
for line in source.read_text('utf-8-sig').splitlines()[1:]:
    if ',' not in line:
        continue
    key, label = line.split(',', 1)
    key = re.sub(r':(\d+)$', r'@\1', key.strip())
    rows.setdefault(key, label.strip())
registry_data = json.loads((ROOT / 'config/gamedata_snapshot.json').read_text('utf-8'))
registry = {k[1:] for k in registry_data['idMap'] if k.startswith('\x02')}
present = {k.split('@')[0] for k in rows}
added = sorted(registry - present)
for key in added:
    rows[key] = ''

def clean(s):
    return re.sub(r'§.', '', s).replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').strip()

def translate(key, label):
    if CYR.search(label):
        return clean(label), 'выгрузка'
    base = key.split('@')[0].split(':', 1)[1]
    candidates = [label, base, 'item.'+base+'.name', 'tile.'+base+'.name', 'ic2.'+base+'.name', re.sub(r'^ic2\.(.+)\.name$', r'\1', label)]
    for candidate in candidates:
        if candidate in ru and CYR.search(ru[candidate]):
            return clean(ru[candidate]), 'локализация'
    if label in english_to_ru:
        return clean(english_to_ru[label]), 'локализация'
    return '', ''

resolved, unresolved = {}, []
for key, label in rows.items():
    name, method = translate(key, label)
    if name:
        resolved[key] = (name, method)
    else:
        unresolved.append((key, label))
for key, label in unresolved:
    resolved[key] = (explanatory(key, label), 'русское пояснение')

ordered = sorted(rows, key=lambda key: (key.split(':')[0].lower(), resolved[key][0].lower(), key))
def write_text(name, lines):
    (OUT / name).write_text('\n'.join(lines)+'\n', encoding='utf-8-sig', newline='\r\n')

write_text('Предметы_Industrial_для_energy.txt', [f'{resolved[key][0]} [{key}]' for key in ordered])
write_text('Предметы_русские_пояснения.txt', [f'{resolved[key][0]} [{key}]' for key in ordered if resolved[key][1] == 'русское пояснение'])
write_text('Предметы_дополнение_из_реестра.txt', [f'{resolved[key][0]} [{key}]' for key in ordered if key in added])

counts = collections.Counter(key.split(':')[0] for key in rows)
unsupported = [k for k in ordered if not re.fullmatch(r'[\w_.-]+:[\w_./:\-]+(?:@\d+)?', k, flags=re.ASCII)]
fallback = [k for k in ordered if resolved[k][0].startswith('Предмет без перевода')]
write_text('Предметы_Industrial_ПРОЧИТАЙ.txt', [
    'КАТАЛОГ ПРЕДМЕТОВ INDUSTRIAL 1.7.10 ДЛЯ ENERGY',
    '',
    f'Источник: {ROOT}',
    f'Уникальных строк: {len(rows)}. Пространств имён модов: {len(counts)}.',
    'Основной файл: Предметы_Industrial_для_energy.txt.',
    'Формат каждой строки: Русское название [mod:item@meta]. Кодировка: UTF-8.',
    'Список отсортирован по моду и русскому названию. Поиск: Ctrl+F.',
    '',
    'КАК ДОБАВЛЯТЬ В ENERGY',
    'В поле «+ ID» вставляйте только содержимое квадратных скобок.',
    'Например: minecraft:iron_ingot или minecraft:dye@4.',
    'Затем укажите русское имя из списка. Energy сохранит его и будет показывать изменение количества.',
    'Этот каталог — справочник. Не копируйте его целиком в /home/energy-items.txt: там другой формат.',
    'Не рекомендуется одновременно отслеживать тысячи предметов: выбирайте нужные.',
    'Суффикс @число различает варианты. ID без @ в energy суммирует все значения damage.',
    'Регистр, точки, двоеточия и символ | в ID необходимо сохранять.',
    '',
    'ИСТОЧНИКИ И ГРАНИЦЫ ПОЛНОТЫ',
    'Прочитаны архивы mods и nestedmods, доступные ресурс-паки и переводы config/txloader.',
    'ID и известные варианты взяты из config/AppliedEnergistics2/recipes/items.csv.',
    f'Выгрузка AE2 содержит {len(source.read_text("utf-8-sig").splitlines())-1} строк; повторяющиеся ID объединены.',
    f'Дополнительно включены {len(added)} базовых ID из config/gamedata_snapshot.json, отсутствующих в выгрузке.',
    'Они отдельно перечислены в Предметы_дополнение_из_реестра.txt; их варианты по meta могут быть не перечислены.',
    'Включены также технические, скрытые и творческие предметы: не каждый можно получить в выживании.',
    'Наличие в локальном реестре не гарантирует доступность на конкретном сервере.',
    'Выгрузка AE2 датирована 15.10.2026. Это полный каталог найденных локальных ID и известных вариантов,',
    'но не доказательство полноты всех актуальных вариантов предметов на сервере.',
    'Пчёлы, саженцы, жидкости в ёмкостях, зачарования и другие NBT-разновидности могут иметь одинаковый ID@meta.',
    'Текущая energy не различает NBT. Такие разновидности объединены по ID; отображается одно имя.',
    '',
    'НАЗВАНИЯ',
    'Использованы русские названия выгрузки и доступных файлов локализации модов.',
    f'Для {len(unresolved)} строк без перевода добавлены русские пояснения; они не являются официальной локализацией.',
    'Эти строки отдельно перечислены в Предметы_русские_пояснения.txt.',
    f'Полностью нерасшифрованных названий: {len(fallback)}. Они помечены «Предмет без перевода».',
    '',
    f'В energy.lua до версии 2026-10-22-item-ids не принимались {len(unsupported)} строк этого каталога.',
    'Причины: символ | в пространстве имён и пробелы в имени предмета.',
    'Парсер в C:\\Lua\\energy.lua обновлён: теперь эти ID BuildCraft, ProjectRed и BiblioCraft допустимы.',
    'Если в игровом компьютере старая версия energy.lua, обновите её из C:\\Lua. ID нельзя изменять.',
    '',
    'КОЛИЧЕСТВО СТРОК ПО МОДАМ',
    *[f'{mod}: {count}' for mod,count in sorted(counts.items(), key=lambda pair: pair[0].lower())],
    '',
    'АРХИВЫ: число файлов перевода (0 — переводов нет; -1 — заглушка или не ZIP)',
    *[f'{path}: {count}' for path,count in inventory],
])
for key in ordered:
    assert CYR.search(resolved[key][0]), (key, resolved[key])
    assert ':' in key and not any(c in key for c in '\r\n\t'), key
    assert '\ufffd' not in resolved[key][0], (key, resolved[key])
assert registry <= {key.split('@')[0] for key in rows}
print('Catalog written; fallback names:', fallback)
print(json.dumps({'total':len(rows),'resolved':len(resolved),'unresolved':len(unresolved),'added_registry':len(added),'unresolved_mods':dict(collections.Counter(k.split(':')[0] for k,v in unresolved))},ensure_ascii=True))
