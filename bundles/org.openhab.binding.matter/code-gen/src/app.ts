import { AnyElement, FieldElement, Matter, ClusterElement, DatatypeElement, AttributeElement, CommandElement, AnyValueElement } from "@project-chip/matter.js/model";
import { LevelControl } from "@project-chip/matter.js/cluster";
import { Logger } from "@project-chip/matter.js/log";
import handlebars from "handlebars";
import { SafeString } from 'handlebars';
import { ByteArray } from "@project-chip/matter.js/util"
import fs from "fs";

interface ExtendedClusterElement extends ClusterElement {
    attributes: AnyValueElement[],
    commands: CommandElement[],
    datatypes: AnyValueElement[],
    enums: AnyValueElement[],
    structs: AnyValueElement[],
    maps: AnyValueElement[],
    typeMapping: Map<string, string | undefined>
}
function toJSON(data: any, space = 2) {
    return JSON.stringify(data, (_, value) => {
        if (typeof value === "bigint") {
            return value.toString();
        }
        if (value instanceof ByteArray) {
            return value.toHex();
        }
        if (value === undefined) {
            return "undefined";
        }
        return value;
    }, space);
}

handlebars.registerHelper('asUpperCamelCase', function (str) {
    return toUpperCamelCase(str);
});

handlebars.registerHelper('asLowerCamelCase', function (str) {
    return toLowerCamelCase(str);
});

handlebars.registerHelper('asEnumField', function (str) {
    return toEnumField(str);
});

handlebars.registerHelper('asHex', function (decimal, length) {
    return toHex(decimal, length);
});

handlebars.registerHelper('isLastElement', function (index: number, count: number) {
    return index >= count - 1;
});
handlebars.registerHelper('isFirstElement', function (index: number) {
    return index == 0;
});
handlebars.registerHelper('isEmpty', function (e: Array<any> | undefined) {
    return e == undefined || e.length == 0
});
handlebars.registerHelper('isDepreciated', function (field) {
    return field.conformance == "D" || field.conformance == "X"
});

function toUpperCamelCase(str: string | undefined) {
    if (str == undefined) {
        return "undefined"
    }
    return str.replace(/(^\w|[_\s]\w)/g, match => match.replace(/[_\s]/, '').toUpperCase());
}

function toLowerCamelCase(str: string): string {
    if (str == undefined) {
        return "undefined"
    }
    return str.replace(/(?:^\w|[_\s]\w)/g, (match, offset) => {
        return offset === 0 ? match.toLowerCase() : match.replace(/[_\s]/, '').toUpperCase();
    });
}

function toEnumField(str: string): string {
    // Check if the string starts with a number and prepend "V" if it does
    if (/^\d/.test(str)) {
        str = 'V' + str;
    }

    // Perform the existing transformations
    return str.replace(/([a-z])([A-Z])/g, '$1_$2')
        .replace(/\s+/g, '_')
        .toUpperCase();
}

function toHex(decimal: number, length = 0) {
    let hex = decimal.toString(16).toUpperCase();
    if (length > 0) {
        hex = hex.padStart(length, '0');
    }
    return `0x${hex}`;
}

/**
 * 
 * @param field Lookup function to map matter native types to Java native types
 * @returns 
 */
function matterNativeTypeToJavaNativeType(field: AnyElement) {
    switch (field.type || field.name) {
        case "bool":
            return "Boolean";
        case "uint8":
        case "uint16":
        case "uint24":
        case "uint32":
            return "Integer";
        case "uint40":
        case "uint48":
        case "uint56":
        case "uint64":
            return "Long";
        case "int8":
        case "int16":
        case "int24":
        case "int32":
            return "Integer";
        case "int40":
        case "int48":
        case "int56":
        case "int64":
            return "Long";
        case "single":
            return "Float";
        case "double":
            return "Double";
        case "octstr":
            return "String";
        case "date":
            return "date";
        case "string":
            return "String";
        case "list":
        case "struct":
        case "map8":
        case "map16":
        case "map32":
        case "map64":
        case "map8":
        case "map16":
        case "map32":
        case "map64":
            //these are complex types and do not map to a Java native type
        default:
            return undefined;
    }
}

function filterDep(e: AnyValueElement) {
    //remove fields flagged as depreciated or not used 
    const children = e.children?.filter(field => {
        const f = field as FieldElement;
        return f.conformance != 'X' && f.conformance != 'D'
    })
    e.children = children as AnyValueElement[];
    return e;
}

/**
 * Type mapper attempts to lookup the Java native type for any matter element, this include Integers, Strings, Booleans, etc...
 * 
 * If there is no matching type, then the matter element is a complex type, like maps, enums and structs
 * These complex types are represented as Java classes, so the mapping will refer to that complex type which
 * will be templated out later in the code
 * 
 * This code also traverses any children of the data type, applying the same logic
 * 
 * @param mappings - existing set of mapping lookups for types
 * @param dt - the data type we are operating on
 * @returns the data type which now includes a new field 'mappedType' on all levels of the object
 */
function typeMapper(mappings: Map<string, string | undefined>, dt: AnyValueElement): any {
    let mappedType: string | undefined;

    if (dt.tag == 'attribute' && dt.type?.startsWith('enum') || dt.type?.startsWith('map') || dt.type?.startsWith('struct')) {
        //these types will be generated as inner classes and will be referred to by name
        mappedType = dt.name;
    } else {
        //this gets raw types
        mappedType = dt.type && mappings.get(dt.type) || dt.type || "String"  //  no types are strings?
    }

    if (mappedType == 'list') {
        //console.log(toJSON(dt))
        const ct = dt.children?.[0].type
        mappedType = `List<${toUpperCamelCase(ct && mappings.get(ct) || ct)}>`;
    }


    //some types reference other clusters, like MediaPlayback.CharacteristicEnum 
    if (mappedType && mappedType.indexOf('.') > 0) {
        const [cName, dtName] = mappedType.split('.');
        mappedType = dtName;
    }
    const children = dt.children?.map((child) => {
        return typeMapper(mappings, child as AnyValueElement) as AnyValueElement
    })
    return {
        ...dt,
        children: children,
        mappedType: mappedType
    }
}

/**
 * Certain clusters have complex inheritance that we don't support yet (and don't need right now)
 */
const skipClusters = new Set(['FixedLabel', 'UserLabel', 'Channel', 'ContentLauncher']);

/**
 * Global types (not in a cluster)
 */
const globalDataTypes = Matter.children.filter(c => c.tag == 'datatype');
const globalAttributes = Matter.children.filter(c => c.tag == 'attribute');

/**
 * Global type mapping lookup, clusters will combine this with their own mapping
 */
const globalTypeMapping = new Map();
globalTypeMapping.set("FabricIndex", "Integer");
globalDataTypes.forEach(dt => {
    //console.log(dt.name, dt.type, matterNativeTypeToJavaNativeType(dt))
    matterNativeTypeToJavaNativeType(dt) && globalTypeMapping.set(dt.name, matterNativeTypeToJavaNativeType(dt))
});
globalAttributes.forEach(dt => matterNativeTypeToJavaNativeType(dt) && globalTypeMapping.set(dt.name, matterNativeTypeToJavaNativeType(dt)));

const clusters = Matter.children.filter(c => c.tag == 'cluster').filter(c => !skipClusters.has(c.name)).map(cluster => {
    //todo reference LevelControl cluster for lookups in ColorControl (seems to be a one off)
    const typeMapping = new Map<string, string | undefined>(globalTypeMapping);
    const dataTypes = cluster.children?.filter(c => c.tag == 'datatype');
    const maps = cluster.children?.filter(c => c.type?.startsWith('map'));
    const enums = cluster.children?.filter(c => c.type?.startsWith('enum'));

    //looks at attributes and command elements that need top level structs/enums to be declared
    function addTypesFromMember(c: AnyElement) {
        if (c.children) {
            if (c.type?.startsWith('enum')) {
                if (enums && enums.findIndex(e => e.name == c.name) < 0) {
                    enums?.push(c as FieldElement);
                }
            } else if (c.type?.startsWith('map')) {
                if (maps && maps.findIndex(e => e.name == c.name) < 0) {
                    maps?.push(c as FieldElement);
                }
            } else if (c.type?.startsWith('struct')) {
                if (structs && structs.findIndex(e => e.name == c.name) < 0) {
                    structs?.push(c as FieldElement);
                }
            }
            (c as any).mappedType = c.name
        }
    }

    dataTypes?.forEach(dt => {
        if (dt.type && dt.type.indexOf('.') > 0) {
            return typeMapping.set(dt.name, dt.type)
        }
        return matterNativeTypeToJavaNativeType(dt) && typeMapping.set(dt.name, matterNativeTypeToJavaNativeType(dt))
    });

    const attributes = cluster.children?.filter(c => c.tag == 'attribute')?.filter((element, index, self) => {
        //remove duplicates
        const dupIndex = self.findIndex(e => e.name === element.name)
        if (dupIndex != index) {
            if (element.conformance?.toString().startsWith('[!')) {
                return false;
            }
        }
        return true
    }).filter(attr => filterDep(attr)).map(dt => typeMapper(typeMapping, dt))

    //some command types reference attribute types (LevelControl Options)
    attributes?.forEach(dt => {
        if (dt.type && dt.type.indexOf('.') > 0) {
            typeMapping.set(dt.name, dt.type)
            return;
        }
        typeMapping.set(dt.name, matterNativeTypeToJavaNativeType(dt) || dt.type)

        //some local Attributes like FeatureMap reference the global attribute type
        if (dt.children) {
            const ga = globalAttributes.find(a => a.name == dt.type)
            if (ga && ga.type?.startsWith('map') && !maps?.find(e => e.name == dt.name)) {
                maps?.push(dt as FieldElement)
            }
        }
    });
    const structs = cluster.children?.filter(dt => dt.type == 'struct').map(dt => typeMapper(typeMapping, dt as AnyValueElement));

    //clean up commands
    const commandsRaw = cluster.children?.filter(c => c.tag == 'command');
    const commands = commandsRaw?.map(command => {
        //some commands reference others 
        if (command.type != undefined) {
            command.children = commandsRaw?.find(c => c.name == command.type)?.children
        }
        return command
    }).map(command => filterDep(command)).filter(c => (c as CommandElement).direction == "request").map(dt => {
        if (dt.type && dt.type.indexOf('.') > 0) {
            typeMapping.set(dt.name, dt.type)
            return;
        }
        const newCommand = typeMapper(typeMapping, dt)
        newCommand.children?.forEach((c: any) => {
            if (c.type?.startsWith('map') && !maps?.find(e => e.name == c.name)) {
                maps?.push(c as FieldElement)
            }
        });
        return newCommand
    });
    //todo print out all the cluster data types and see why command Option is not mapping to OptionBitmap
    return {
        ...cluster,
        attributes: attributes,
        commands: commands,
        datatypes: dataTypes,
        enums: enums,
        structs: structs,
        maps: maps,
        typeMapping: typeMapping
    } as ExtendedClusterElement
})

function copyClusterDatatype(sourceCluster: ExtendedClusterElement, destCluster: ExtendedClusterElement, name: string) {
    let dt = sourceCluster.datatypes?.find(d => d.name == name) || sourceCluster.enums?.find(d => d.name == name) || sourceCluster.structs?.find(d => d.name == name) || sourceCluster.attributes?.find(d => d.name == name)
    if (dt) {
        destCluster.typeMapping.set(name, name);
        if (dt.type) {
            if (dt.type.startsWith('enum')) {
                if (!destCluster.enums) {
                    destCluster.enums = []
                }
                destCluster.enums.push(dt)
            } else if (dt.type.startsWith('map')) {
                if (!destCluster.maps) {
                    destCluster.maps = []
                }
                destCluster.maps.push(dt)
            } else if (dt.type == 'struct') {
                if (!destCluster.structs) {
                    destCluster.structs = []
                }
                destCluster.structs.push(dt)
            } else {
                if (!destCluster.datatypes) {
                    destCluster.datatypes = []
                }
                destCluster.datatypes.push(dt);
            }
        }
        destCluster.commands = destCluster.commands.map(c => typeMapper(destCluster.typeMapping, c))

    }
}
clusters.forEach(cluster => {
    cluster.typeMapping.forEach((value, key, map) => {
        if (value && value.indexOf('.') > 0) {
            const [cName, dtName] = value.split('.');
            const otherCluster = clusters.find(c => c.name != cluster.name && c.name == cName)
            if (otherCluster) {
                copyClusterDatatype(otherCluster, cluster, dtName);
            }
            return;
        }
    });
});

const lcc = clusters.find(c => c.name == 'LevelControl')
const ccc = clusters.find(c => c.name == 'ColorControl')
if (lcc && ccc) {
    copyClusterDatatype(lcc, ccc, 'Options')
}


// Compile Handlebars template
const clusterSource = fs.readFileSync('src/templates/cluster-class.hbs', 'utf8');
const clusterTemplate = handlebars.compile(clusterSource);
const dataTypeSource = fs.readFileSync('src/templates/data-types-class.hbs', 'utf8');
const dataTypeTemplate = handlebars.compile(dataTypeSource);
const thingTypeSource = fs.readFileSync('src/templates/cluster-thing-types-class.hbs', 'utf8');
const thingTypeTemplate = handlebars.compile(thingTypeSource);

// Generate Java code


const datatypes = {
    enums: [
        ...globalDataTypes?.filter(c => c.type?.startsWith('enum')),
        ...globalAttributes?.filter(c => c.type?.startsWith('enum'))
    ],
    structs: [
        ...globalDataTypes?.filter(c => c.type?.startsWith('struct')),
        ...globalAttributes?.filter(c => c.type?.startsWith('struct'))
    ].map(e => typeMapper(globalTypeMapping, e)),
    maps: [
        ...globalDataTypes?.filter(c => c.type?.startsWith('map')),
        ...globalAttributes?.filter(c => c.type?.startsWith('map'))
    ]
}

fs.mkdir('out', { recursive: true }, (err) => {
});
const dataTypeClass = dataTypeTemplate(datatypes);
fs.writeFileSync(`out/DataTypes.java`, dataTypeClass);

const thingTypesClass = thingTypeTemplate({ clusters: clusters });
fs.writeFileSync(`out/ClusterThingTypes.java`, thingTypesClass);

clusters.forEach(cluster => {
    if (cluster.id == undefined) {
        return;
    }
    const javaCode = clusterTemplate(cluster);
    fs.writeFileSync(`out/${cluster.name}Cluster.java`, javaCode);
});


