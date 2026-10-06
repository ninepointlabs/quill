use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jlong, jstring};
use nostrdb::{Config, Filter, Ndb, Transaction};

#[no_mangle]
pub extern "system" fn Java_com_ninepointlabs_quill_data_NostrDb_ndbOpen<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    path: JString<'local>,
) -> jlong {
    let path_str: String = match env.get_string(&path) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    let config = Config::new();
    match Ndb::new(&path_str, &config) {
        Ok(ndb) => {
            // Box the Ndb to return a raw pointer to Kotlin
            let ptr = Box::into_raw(Box::new(ndb));
            ptr as jlong
        }
        Err(_) => 0,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_ninepointlabs_quill_data_NostrDb_ndbClose<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
) {
    if ptr != 0 {
        // Take ownership back and drop it
        let _ndb = unsafe { Box::from_raw(ptr as *mut Ndb) };
    }
}

#[no_mangle]
pub extern "system" fn Java_com_ninepointlabs_quill_data_NostrDb_ndbIngestEvent<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
    event_json: JString<'local>,
) -> jboolean {
    if ptr == 0 {
        return 0;
    }

    let ndb = unsafe { &*(ptr as *mut Ndb) };
    let json_str: String = match env.get_string(&event_json) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    match ndb.process_event(&json_str) {
        Ok(_) => 1,
        Err(_) => 0,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_ninepointlabs_quill_data_NostrDb_ndbQueryNotes<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
    limit: jint,
) -> jstring {
    if ptr == 0 {
        return env.new_string("[]").unwrap().into_raw();
    }

    let ndb = unsafe { &*(ptr as *mut Ndb) };
    
    let mut notes_json = Vec::new();

    if let Ok(txn) = Transaction::new(ndb) {
        let filters = vec![Filter::new().kinds(vec![1]).limit(limit as u64).build()];
        if let Ok(results) = ndb.query(&txn, &filters, limit) {
            for res in results {
                if let Ok(json) = res.note.json() {
                    notes_json.push(json);
                }
            }
        }
    }

    let array_json = format!("[{}]", notes_json.join(","));
    env.new_string(array_json).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_ninepointlabs_quill_data_NostrDb_ndbCountEvents<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
) -> jlong {
    if ptr == 0 {
        return 0;
    }

    let ndb = unsafe { &*(ptr as *mut Ndb) };
    if let Ok(txn) = Transaction::new(ndb) {
        let filters = vec![Filter::new().build()];
        if let Ok(count) = ndb.count(&txn, &filters) {
            return count as jlong;
        }
    }
    0
}
