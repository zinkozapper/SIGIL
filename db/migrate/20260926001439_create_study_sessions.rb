class CreateStudySessions < ActiveRecord::Migration[8.1]
  def change
    create_table :study_sessions do |t|
      t.string :status, null: false
      t.datetime :started_at, null: false
      t.datetime :ended_at, null: false
      t.integer :duration_seconds, null: false
      t.string :subject
      t.string :chapter
      t.text :notes
      t.text :metadata
      t.string :client_session_id, null: false

      t.timestamps
    end

    add_index :study_sessions, :client_session_id, unique: true
    add_index :study_sessions, :started_at
    add_index :study_sessions, :subject
    add_index :study_sessions, :chapter
    add_check_constraint :study_sessions,
      "ended_at >= started_at",
      name: "study_session_times_check"

    add_check_constraint :study_sessions,
      "duration_seconds >= 0",
      name: "study_session_duration_check"
  end
end
