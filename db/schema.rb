# This file is auto-generated from the current state of the database. Instead
# of editing this file, please use the migrations feature of Active Record to
# incrementally modify your database, and then regenerate this schema definition.
#
# This file is the source Rails uses to define your schema when running `bin/rails
# db:schema:load`. When creating a new database, `bin/rails db:schema:load` tends to
# be faster and is potentially less error prone than running all of your
# migrations from scratch. Old migrations may fail to apply correctly if those
# migrations use external dependencies or application code.
#
# It's strongly recommended that you check this file into your version control system.

ActiveRecord::Schema[8.1].define(version: 2026_10_07_000000) do
  create_table "study_sessions", force: :cascade do |t|
    t.string "chapter"
    t.string "client_session_id", null: false
    t.datetime "created_at", null: false
    t.integer "duration_seconds", null: false
    t.datetime "ended_at", null: false
    t.text "metadata"
    t.text "notes"
    t.datetime "started_at", null: false
    t.string "subject"
    t.datetime "updated_at", null: false
    t.index ["chapter"], name: "index_study_sessions_on_chapter"
    t.index ["client_session_id"], name: "index_study_sessions_on_client_session_id", unique: true
    t.index ["started_at"], name: "index_study_sessions_on_started_at"
    t.index ["subject"], name: "index_study_sessions_on_subject"
    t.check_constraint "duration_seconds >= 0", name: "study_session_duration_check"
    t.check_constraint "ended_at >= started_at", name: "study_session_times_check"
  end
end
